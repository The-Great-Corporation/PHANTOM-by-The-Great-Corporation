"""Tests for remembered enrollment and seamless reconnection protocol."""

import asyncio
import json
import unittest
from unittest.mock import AsyncMock, MagicMock

from core.session_security import (
    EnrolledDeviceExpiredError,
    EnrolledDeviceRevokedError,
    InvalidMacError,
    InvalidReconnectChallengeError,
    SessionSecurity,
    SessionSecurityError,
    UnknownEnrolledDeviceError,
    compute_pairing_proof,
    compute_reconnect_proof,
)
from core.stream_security import StreamAuthenticator
from protocols.bluetooth_server import BluetoothServer
from protocols.udp_server import UDPServer
from protocols.usb_server import USBServer
from protocols.websocket_server import WebSocketServer


class TestRememberedEnrollment(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.now = 1000.0
        self.security = SessionSecurity(
            clock=lambda: self.now,
            remembered_device_ttl_seconds=3600.0,
            reconnect_challenge_ttl_seconds=10.0,
        )

    def test_1_first_qr_pairing_creates_enrollment(self):
        """1. premier pairing QR et création de l'enrôlement"""
        token = self.security.issue_pairing_token("device1", ttl_seconds=60.0)
        challenge = self.security.create_pairing_challenge(
            token.token_id, "device1", "nonce1", ("127.0.0.1", 1234)
        )
        proof = compute_pairing_proof(
            token.value,
            token.token_id,
            "device1",
            "nonce1",
            challenge.challenge_id,
            challenge.challenge,
        )
        session = self.security.consume_pairing_challenge(
            challenge.challenge_id,
            challenge.challenge,
            token.token_id,
            "device1",
            "nonce1",
            proof,
            ("127.0.0.1", 1234),
        )
        self.assertIsNotNone(session)
        enrolled = self.security.get_enrolled_device("device1")
        self.assertIsNotNone(enrolled)
        self.assertEqual(enrolled.device_id, "device1")
        self.assertEqual(enrolled.enrollment_secret, token.value)
        self.assertEqual(enrolled.registered_at, 1000.0)
        self.assertEqual(enrolled.last_activity, 1000.0)
        self.assertFalse(enrolled.is_revoked)

    def test_2_valid_reconnection_without_qr(self):
        """2. reconnexion valide sans QR"""
        # Enroll device first
        token = self.security.issue_pairing_token("device1", ttl_seconds=60.0)
        ch1 = self.security.create_pairing_challenge(
            token.token_id, "device1", "n1", ("127.0.0.1", 1234)
        )
        p1 = compute_pairing_proof(
            token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge
        )
        self.security.consume_pairing_challenge(
            ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234)
        )

        # Advance time by 300s (QR token is now consumed & expired!)
        self.now = 1300.0

        # Attempt reconnect
        ch2 = self.security.create_reconnect_challenge(
            "device1", "n2", ("127.0.0.1", 1234)
        )
        p2 = compute_reconnect_proof(
            token.value, "device1", "n2", ch2.challenge_id, ch2.challenge
        )
        s2 = self.security.consume_reconnect_challenge(
            ch2.challenge_id, ch2.challenge, "device1", "n2", p2, ("127.0.0.1", 1234)
        )
        self.assertIsNotNone(s2)
        self.assertEqual(s2.identity.device_id, "device1")

    def test_3_incorrect_secret_rejected(self):
        """3. secret incorrect"""
        token = self.security.issue_pairing_token("device1")
        ch1 = self.security.create_pairing_challenge(token.token_id, "device1", "n1", ("127.0.0.1", 1234))
        p1 = compute_pairing_proof(token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234))

        ch2 = self.security.create_reconnect_challenge("device1", "n2", ("127.0.0.1", 1234))
        bad_proof = compute_reconnect_proof("wrong_secret_32_bytes_long_value_x", "device1", "n2", ch2.challenge_id, ch2.challenge)
        with self.assertRaises(InvalidMacError):
            self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", bad_proof, ("127.0.0.1", 1234))

    def test_4_unknown_device_id_rejected(self):
        """4. device_id inconnu"""
        with self.assertRaises(UnknownEnrolledDeviceError):
            self.security.create_reconnect_challenge("unknown_device", "n1", ("127.0.0.1", 1234))

    def test_5_expired_reconnect_challenge_rejected(self):
        """5. challenge expiré"""
        token = self.security.issue_pairing_token("device1")
        ch1 = self.security.create_pairing_challenge(token.token_id, "device1", "n1", ("127.0.0.1", 1234))
        p1 = compute_pairing_proof(token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234))

        ch2 = self.security.create_reconnect_challenge("device1", "n2", ("127.0.0.1", 1234), ttl_seconds=5.0)
        self.now = 1010.0  # Challenge expired
        p2 = compute_reconnect_proof(token.value, "device1", "n2", ch2.challenge_id, ch2.challenge)
        with self.assertRaises(InvalidReconnectChallengeError):
            self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", p2, ("127.0.0.1", 1234))

    def test_6_replayed_challenge_rejected(self):
        """6. challenge rejoué"""
        token = self.security.issue_pairing_token("device1")
        ch1 = self.security.create_pairing_challenge(token.token_id, "device1", "n1", ("127.0.0.1", 1234))
        p1 = compute_pairing_proof(token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234))

        ch2 = self.security.create_reconnect_challenge("device1", "n2", ("127.0.0.1", 1234))
        p2 = compute_reconnect_proof(token.value, "device1", "n2", ch2.challenge_id, ch2.challenge)
        self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", p2, ("127.0.0.1", 1234))

        # Replay same challenge
        with self.assertRaises(InvalidReconnectChallengeError):
            self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", p2, ("127.0.0.1", 1234))

    def test_7_replayed_proof_rejected(self):
        """7. preuve rejouée"""
        token = self.security.issue_pairing_token("device1")
        ch1 = self.security.create_pairing_challenge(token.token_id, "device1", "n1", ("127.0.0.1", 1234))
        p1 = compute_pairing_proof(token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234))

        ch2 = self.security.create_reconnect_challenge("device1", "n2", ("127.0.0.1", 1234))
        p2 = compute_reconnect_proof(token.value, "device1", "n2", ch2.challenge_id, ch2.challenge)
        s2 = self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", p2, ("127.0.0.1", 1234))
        self.assertIsNotNone(s2)

        # Attempt to reuse p2
        with self.assertRaises(InvalidReconnectChallengeError):
            self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", p2, ("127.0.0.1", 1234))

    def test_8_ttl_expiration_requires_repairing(self):
        """8. expiration du TTL"""
        token = self.security.issue_pairing_token("device1")
        ch1 = self.security.create_pairing_challenge(token.token_id, "device1", "n1", ("127.0.0.1", 1234))
        p1 = compute_pairing_proof(token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234))

        # Advance past 3600s TTL
        self.now = 5000.0
        with self.assertRaises(EnrolledDeviceExpiredError):
            self.security.create_reconnect_challenge("device1", "n2", ("127.0.0.1", 1234))

    def test_9_sliding_renewal_after_valid_reconnection(self):
        """9. renouvellement glissant après reconnexion valide"""
        token = self.security.issue_pairing_token("device1")
        ch1 = self.security.create_pairing_challenge(token.token_id, "device1", "n1", ("127.0.0.1", 1234))
        p1 = compute_pairing_proof(token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234))

        # Advance by 3000s (< 3600s TTL)
        self.now = 4000.0
        ch2 = self.security.create_reconnect_challenge("device1", "n2", ("127.0.0.1", 1234))
        p2 = compute_reconnect_proof(token.value, "device1", "n2", ch2.challenge_id, ch2.challenge)
        self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", p2, ("127.0.0.1", 1234))

        # Last activity is now 4000.0! Advance by another 3000s to 7000.0 (total 6000s from initial pairing)
        self.now = 7000.0
        ch3 = self.security.create_reconnect_challenge("device1", "n3", ("127.0.0.1", 1234))
        p3 = compute_reconnect_proof(token.value, "device1", "n3", ch3.challenge_id, ch3.challenge)
        s3 = self.security.consume_reconnect_challenge(ch3.challenge_id, ch3.challenge, "device1", "n3", p3, ("127.0.0.1", 1234))
        self.assertIsNotNone(s3)

    def test_10_failed_attempt_does_not_renew_ttl(self):
        """10. tentative échouée ne renouvelant pas le TTL"""
        token = self.security.issue_pairing_token("device1")
        ch1 = self.security.create_pairing_challenge(token.token_id, "device1", "n1", ("127.0.0.1", 1234))
        p1 = compute_pairing_proof(token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234))

        # Advance to 3500s (< 3600s)
        self.now = 4500.0
        ch2 = self.security.create_reconnect_challenge("device1", "n2", ("127.0.0.1", 1234))
        bad_proof = "invalid_proof"
        with self.assertRaises(InvalidMacError):
            self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", bad_proof, ("127.0.0.1", 1234))

        # Advance by 200s (now 4700s > 1000 + 3600 = 4600s TTL)
        self.now = 4700.0
        with self.assertRaises(EnrolledDeviceExpiredError):
            self.security.create_reconnect_challenge("device1", "n3", ("127.0.0.1", 1234))

    def test_11_new_session_id_per_reconnection(self):
        """11. nouvelle session_id à chaque reconnexion"""
        token = self.security.issue_pairing_token("device1")
        ch1 = self.security.create_pairing_challenge(token.token_id, "device1", "n1", ("127.0.0.1", 1234))
        p1 = compute_pairing_proof(token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge)
        s1 = self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234))

        ch2 = self.security.create_reconnect_challenge("device1", "n2", ("127.0.0.1", 1234))
        p2 = compute_reconnect_proof(token.value, "device1", "n2", ch2.challenge_id, ch2.challenge)
        s2 = self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", p2, ("127.0.0.1", 1234))

        self.assertNotEqual(s1.identity.session_id, s2.identity.session_id)
        self.assertNotEqual(s1.shared_secret, s2.shared_secret)

    def test_12_application_messages_hmac_protected(self):
        """12. messages applicatifs toujours protégés par HMAC"""
        token = self.security.issue_pairing_token("device1")
        ch1 = self.security.create_pairing_challenge(token.token_id, "device1", "n1", ("127.0.0.1", 1234))
        p1 = compute_pairing_proof(token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234))

        ch2 = self.security.create_reconnect_challenge("device1", "n2", ("127.0.0.1", 1234))
        p2 = compute_reconnect_proof(token.value, "device1", "n2", ch2.challenge_id, ch2.challenge)
        s2 = self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", p2, ("127.0.0.1", 1234))

        msg = {"type": "input", "client_id": "device1", "session_id": s2.identity.session_id, "payload": {"a": True}}
        mac = self.security.compute_mac(s2.identity.session_id, 0, msg)
        self.security.verify_mac(s2.identity.session_id, 0, msg, mac)

        # Tampered message fails
        tampered = {"type": "input", "client_id": "device1", "session_id": s2.identity.session_id, "payload": {"a": False}}
        with self.assertRaises(InvalidMacError):
            self.security.verify_mac(s2.identity.session_id, 1, tampered, mac)

    def test_13_replayed_sequence_rejected_after_reconnection(self):
        """13. séquence rejouée rejetée après reconnexion"""
        token = self.security.issue_pairing_token("device1")
        ch1 = self.security.create_pairing_challenge(token.token_id, "device1", "n1", ("127.0.0.1", 1234))
        p1 = compute_pairing_proof(token.value, token.token_id, "device1", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "device1", "n1", p1, ("127.0.0.1", 1234))

        ch2 = self.security.create_reconnect_challenge("device1", "n2", ("127.0.0.1", 1234))
        p2 = compute_reconnect_proof(token.value, "device1", "n2", ch2.challenge_id, ch2.challenge)
        s2 = self.security.consume_reconnect_challenge(ch2.challenge_id, ch2.challenge, "device1", "n2", p2, ("127.0.0.1", 1234))

        msg = {"type": "input", "client_id": "device1", "session_id": s2.identity.session_id, "payload": {"a": True}}
        mac0 = self.security.compute_mac(s2.identity.session_id, 0, msg)
        self.security.verify_mac(s2.identity.session_id, 0, msg, mac0)

        # Replay sequence 0
        with self.assertRaises(SessionSecurityError):
            self.security.verify_mac(s2.identity.session_id, 0, msg, mac0)

    async def test_14_udp_reconnection_behavior(self):
        """14. comportement UDP"""
        cm = MagicMock()
        cm.get_client.return_value = None
        cm.get_client_count.return_value = 0
        cm.max_clients = 4
        cm.connect_client = AsyncMock(return_value=True)

        udp_server = UDPServer("127.0.0.1", 8888, cm, MagicMock(), MagicMock(), security=self.security)
        udp_server.transport = MagicMock()

        # Enroll device
        token = self.security.issue_pairing_token("udp_device")
        ch1 = self.security.create_pairing_challenge(token.token_id, "udp_device", "n1", ("127.0.0.1", 5000))
        p1 = compute_pairing_proof(token.value, token.token_id, "udp_device", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "udp_device", "n1", p1, ("127.0.0.1", 5000))

        # Reconnect begin
        await udp_server._handle_reconnect_begin(
            {"type": "reconnect_begin", "device_id": "udp_device", "client_nonce": "cn2"},
            ("127.0.0.1", 5000)
        )
        sent_args = udp_server.transport.sendto.call_args[0]
        challenge_resp = json.loads(sent_args[0].decode('utf-8'))
        self.assertEqual(challenge_resp["type"], "reconnect_challenge")

        # Reconnect proof
        proof = compute_reconnect_proof(token.value, "udp_device", "cn2", challenge_resp["challenge_id"], challenge_resp["challenge"])
        await udp_server._handle_reconnect_proof(
            {
                "type": "reconnect_proof",
                "device_id": "udp_device",
                "client_nonce": "cn2",
                "challenge_id": challenge_resp["challenge_id"],
                "challenge": challenge_resp["challenge"],
                "proof": proof,
            },
            ("127.0.0.1", 5000)
        )
        final_args = udp_server.transport.sendto.call_args[0]
        ack_resp = json.loads(final_args[0].decode('utf-8'))
        self.assertEqual(ack_resp["type"], "connected")
        self.assertEqual(ack_resp["device_id"], "udp_device")
        self.assertIn("session_id", ack_resp)

    async def test_15_websocket_reconnection_behavior(self):
        """15. comportement WebSocket"""
        cm = MagicMock()
        cm.connect_client = AsyncMock(return_value=True)
        ws_server = WebSocketServer("127.0.0.1", 8889, cm, MagicMock(), MagicMock(), security=self.security)

        # Enroll device
        token = self.security.issue_pairing_token("ws_device")
        ch1 = self.security.create_pairing_challenge(token.token_id, "ws_device", "n1", ("127.0.0.1", 5001))
        p1 = compute_pairing_proof(token.value, token.token_id, "ws_device", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "ws_device", "n1", p1, ("127.0.0.1", 5001))

        # Reconnect begin
        resp1 = ws_server.authenticator.begin_reconnect(
            {"type": "reconnect_begin", "device_id": "ws_device", "client_nonce": "cn2"},
            ("127.0.0.1", 5001)
        )
        self.assertEqual(resp1["type"], "reconnect_challenge")

        # Reconnect proof
        proof = compute_reconnect_proof(token.value, "ws_device", "cn2", resp1["challenge_id"], resp1["challenge"])
        session = ws_server.authenticator.complete_reconnect(
            {
                "type": "reconnect_proof",
                "device_id": "ws_device",
                "client_nonce": "cn2",
                "challenge_id": resp1["challenge_id"],
                "challenge": resp1["challenge"],
                "proof": proof,
            },
            ("127.0.0.1", 5001)
        )
        self.assertEqual(session.identity.device_id, "ws_device")

    async def test_16_usb_reconnection_behavior(self):
        """16. comportement USB"""
        cm = MagicMock()
        cm.connect_client = AsyncMock(return_value=True)
        usb_server = USBServer(8890, cm, MagicMock(), MagicMock(), MagicMock(), security=self.security)

        # Enroll device
        token = self.security.issue_pairing_token("usb_device")
        ch1 = self.security.create_pairing_challenge(token.token_id, "usb_device", "n1", ("127.0.0.1", 5002))
        p1 = compute_pairing_proof(token.value, token.token_id, "usb_device", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "usb_device", "n1", p1, ("127.0.0.1", 5002))

        # Stream authenticator handles USB reconnection
        resp1 = usb_server.authenticator.begin_reconnect(
            {"type": "reconnect_begin", "device_id": "usb_device", "client_nonce": "cn2"},
            ("127.0.0.1", 5002)
        )
        self.assertEqual(resp1["type"], "reconnect_challenge")

        proof = compute_reconnect_proof(token.value, "usb_device", "cn2", resp1["challenge_id"], resp1["challenge"])
        session = usb_server.authenticator.complete_reconnect(
            {
                "type": "reconnect_proof",
                "device_id": "usb_device",
                "client_nonce": "cn2",
                "challenge_id": resp1["challenge_id"],
                "challenge": resp1["challenge"],
                "proof": proof,
            },
            ("127.0.0.1", 5002)
        )
        self.assertEqual(session.identity.device_id, "usb_device")

    async def test_17_rfcomm_reconnection_behavior(self):
        """17. comportement RFCOMM serveur"""
        cm = MagicMock()
        cm.connect_client = AsyncMock(return_value=True)
        bt_server = BluetoothServer(8887, cm, MagicMock(), MagicMock(), security=self.security)

        # Enroll device
        token = self.security.issue_pairing_token("bt_device")
        ch1 = self.security.create_pairing_challenge(token.token_id, "bt_device", "n1", ("127.0.0.1", 5003))
        p1 = compute_pairing_proof(token.value, token.token_id, "bt_device", "n1", ch1.challenge_id, ch1.challenge)
        self.security.consume_pairing_challenge(ch1.challenge_id, ch1.challenge, token.token_id, "bt_device", "n1", p1, ("127.0.0.1", 5003))

        resp1 = bt_server.authenticator.begin_reconnect(
            {"type": "reconnect_begin", "device_id": "bt_device", "client_nonce": "cn2"},
            ("127.0.0.1", 5003)
        )
        self.assertEqual(resp1["type"], "reconnect_challenge")

        proof = compute_reconnect_proof(token.value, "bt_device", "cn2", resp1["challenge_id"], resp1["challenge"])
        session = bt_server.authenticator.complete_reconnect(
            {
                "type": "reconnect_proof",
                "device_id": "bt_device",
                "client_nonce": "cn2",
                "challenge_id": resp1["challenge_id"],
                "challenge": resp1["challenge"],
                "proof": proof,
            },
            ("127.0.0.1", 5003)
        )
        self.assertEqual(session.identity.device_id, "bt_device")

    def test_18_no_regression_existing_qr_pairing(self):
        """18. absence de régression du pairing QR existant"""
        token = self.security.issue_pairing_token("qr_device")
        ch = self.security.create_pairing_challenge(token.token_id, "qr_device", "n1", ("127.0.0.1", 1234))
        p = compute_pairing_proof(token.value, token.token_id, "qr_device", "n1", ch.challenge_id, ch.challenge)
        s = self.security.consume_pairing_challenge(ch.challenge_id, ch.challenge, token.token_id, "qr_device", "n1", p, ("127.0.0.1", 1234))
        self.assertEqual(s.identity.device_id, "qr_device")


if __name__ == "__main__":
    unittest.main()
