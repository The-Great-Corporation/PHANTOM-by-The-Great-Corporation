import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.session_security import (
    InvalidMacError,
    PairingTokenConsumedError,
    PairingTokenExpiredError,
    SequenceAlreadySeenError,
    SessionSecurity,
    UnknownSessionError,
    compute_pairing_proof,
)


def consume(security, token, device_id):
    nonce = "nonce-" + device_id
    challenge = "challenge-" + device_id
    proof = compute_pairing_proof(token.value, token.token_id, device_id, nonce)
    return security.consume_pairing_token(
        token.token_id,
        proof,
        device_id=device_id,
        client_nonce=nonce,
        server_challenge=challenge,
    )


class TestSessionSecurity(unittest.TestCase):
    def setUp(self):
        self.now = 100.0
        self.security = SessionSecurity(clock=lambda: self.now)

    def test_pairing_tokens_are_random_and_single_use(self):
        first = self.security.issue_pairing_token("phone-a")
        second = self.security.issue_pairing_token("phone-a")
        self.assertNotEqual(first.value, second.value)

        proof = compute_pairing_proof(first.value, first.token_id, "phone-a", "nonce")
        session = self.security.consume_pairing_token(
            first.token_id, proof, device_id="phone-a",
            client_nonce="nonce", server_challenge="challenge"
        )
        self.assertEqual(session.identity.device_id, "phone-a")
        with self.assertRaises(PairingTokenConsumedError):
            self.security.consume_pairing_token(
                first.token_id, proof, device_id="phone-a",
                client_nonce="nonce", server_challenge="challenge"
            )

    def test_expired_token_is_rejected(self):
        token = self.security.issue_pairing_token("phone-a", ttl_seconds=10)
        self.now = 110.0
        with self.assertRaises(PairingTokenExpiredError):
            self.security.consume_pairing_token(
                token.token_id,
                compute_pairing_proof(token.value, token.token_id, "phone-a", "nonce"),
                device_id="phone-a", client_nonce="nonce", server_challenge="challenge"
            )

    def test_valid_mac_and_message_tampering(self):
        session = consume(
            self.security, self.security.issue_pairing_token("phone-a"), "phone-a"
        )
        message = {"a": True, "value": 1}
        mac = self.security.compute_mac(session.identity.session_id, 7, message)
        self.assertEqual(
            mac,
            self.security.compute_mac(
                session.identity.session_id, 7, {"value": 1, "a": True}
            ),
        )
        self.security.verify_mac(session.identity.session_id, 7, message, mac)

        altered_mac = self.security.compute_mac(
            session.identity.session_id, 7, {"a": False, "value": 1}
        )
        with self.assertRaises(InvalidMacError):
            self.security.verify_mac(
                session.identity.session_id, 7, message, altered_mac
            )
        with self.assertRaises(InvalidMacError):
            self.security.verify_mac(
                session.identity.session_id, 8, message, b"invalid"
            )

    def test_replayed_sequence_is_rejected(self):
        session = consume(
            self.security, self.security.issue_pairing_token("phone-a"), "phone-a"
        )
        message = {"input": "state"}
        mac = self.security.compute_mac(session.identity.session_id, 1, message)
        self.security.verify_mac(session.identity.session_id, 1, message, mac)
        with self.assertRaises(SequenceAlreadySeenError):
            self.security.verify_mac(session.identity.session_id, 1, message, mac)

    def test_sessions_are_isolated(self):
        first = consume(
            self.security, self.security.issue_pairing_token("phone-a"), "phone-a"
        )
        second = consume(
            self.security, self.security.issue_pairing_token("phone-b"), "phone-b"
        )
        message = {"input": "state"}
        first_mac = self.security.compute_mac(first.identity.session_id, 1, message)
        with self.assertRaises(InvalidMacError):
            self.security.verify_mac(
                second.identity.session_id, 1, message, first_mac
            )
        with self.assertRaises(UnknownSessionError):
            self.security.verify_mac("missing", 1, message, first_mac)


if __name__ == "__main__":
    unittest.main()
