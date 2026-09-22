import asyncio
import hashlib
import hmac
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.connection_manager import ConnectionManager
from core.session_security import (
    SessionSecurity,
    canonicalize_message,
    compute_pairing_proof,
    derive_session_secret,
)
from protocols.udp_server import UDPServer


class MockTransport:
    def __init__(self):
        self.sent = []

    def sendto(self, data, addr):
        self.sent.append((data, addr))


class MockEmulator:
    def __init__(self):
        self.last_state = None

    async def update_state(self, state, client_id=None):
        self.last_state = state


class TestUDPSecurity(unittest.TestCase):
    def setUp(self):
        self.config = {
            "connection": {"max_clients": 2, "timeout": 10},
            "gamepad": {},
        }

    async def _server(self, max_clients=2, clock=None):
        config = self.config | {
            "connection": {"max_clients": max_clients, "timeout": 10}
        }
        cm = ConnectionManager(config)
        await cm.start()
        security = SessionSecurity(clock=clock) if clock else SessionSecurity()
        emulator = MockEmulator()
        udp = UDPServer("127.0.0.1", 8888, cm, emulator, object(), security=security)
        udp.transport = MockTransport()
        return cm, security, emulator, udp

    async def _pair(self, udp, security, device_id="phone-a", nonce="nonce-a"):
        token = security.issue_pairing_token(device_id)
        begin = {
            "type": "pair_begin",
            "device_id": device_id,
            "token_id": token.token_id,
            "client_nonce": nonce,
        }
        addr = ("127.0.0.1", 54321)
        await udp._handle_datagram(json.dumps(begin).encode(), addr)
        challenge_response = json.loads(udp.transport.sent[-1][0])
        proof_message = {
            **begin,
            "challenge_id": challenge_response["challenge_id"],
            "challenge": challenge_response["challenge"],
            "proof": compute_pairing_proof(
                token.value,
                token.token_id,
                device_id,
                nonce,
                challenge_response["challenge_id"],
                challenge_response["challenge"],
            ),
            "type": "pair_proof",
        }
        wire_message = json.dumps(proof_message).encode()
        self.assertNotIn(b'"token":', wire_message)
        self.assertNotIn(token.value.encode(), wire_message)
        await udp._handle_datagram(wire_message, addr)
        response = json.loads(udp.transport.sent[-1][0])
        return response, token, nonce

    async def _control(self, udp, security, session, sequence=0, data=None, mac=None):
        payload = data or {"a": True}
        unsigned = {
            "type": "input",
            "client_id": session["client_id"],
            "session_id": session["session_id"],
            "payload": payload,
        }
        if mac is None:
            mac = security.compute_mac(session["session_id"], sequence, unsigned).hex()
        await udp._handle_datagram(json.dumps({
            "type": "input",
            "client_id": session["client_id"],
            "session_id": session["session_id"],
            "sequence": sequence,
            "mac": mac,
            "data": payload,
        }).encode(), ("127.0.0.1", 54321))

    def test_handshake_success_and_authenticated_input(self):
        async def run():
            cm, security, emulator, udp = await self._server()
            response, token, nonce = await self._pair(udp, security)
            session = {"client_id": "phone-a", "session_id": response["session_id"]}
            self.assertEqual(response["type"], "pair_ack")
            self.assertEqual(response["confirmation"], "paired")
            self.assertNotIn("session_secret", response)
            client_secret = derive_session_secret(
                token.value, nonce, response["server_challenge"], response["session_id"]
            )
            payload = {"a": True}
            unsigned = {
                "type": "input",
                "client_id": "phone-a",
                "session_id": response["session_id"],
                "payload": payload,
            }
            mac = hmac.new(
                client_secret,
                canonicalize_message(0, unsigned),
                hashlib.sha256,
            ).hexdigest()
            await self._control(udp, security, session, mac=mac)
            self.assertTrue(emulator.last_state.a)
            await cm.stop()
        asyncio.run(run())

    def test_invalid_and_expired_tokens_rejected(self):
        async def run():
            now = [100.0]
            cm, security, _, udp = await self._server(clock=lambda: now[0])
            await udp._handle_datagram(json.dumps({
                "type": "pair_begin", "device_id": "phone-a",
                "token_id": "not-issued", "proof": "00" * 32,
                "client_nonce": "nonce",
            }).encode(), ("127.0.0.1", 54321))
            self.assertEqual(len(udp.transport.sent), 0)
            token = security.issue_pairing_token("phone-a", ttl_seconds=1)
            now[0] = 101.0
            await udp._handle_datagram(json.dumps({
                "type": "pair_begin", "device_id": "phone-a",
                "token_id": token.token_id,
                "client_nonce": "nonce",
            }).encode(), ("127.0.0.1", 54321))
            self.assertEqual(len(udp.transport.sent), 0)
            await cm.stop()
        asyncio.run(run())

    def test_invalid_proof_does_not_consume_token(self):
        async def run():
            cm, security, _, udp = await self._server()
            token = security.issue_pairing_token("phone-a")
            await udp._handle_datagram(json.dumps({
                "type": "pair_begin",
                "device_id": "phone-a",
                "token_id": token.token_id,
                "client_nonce": "nonce-a",
            }).encode(), ("127.0.0.1", 54321))
            challenge = json.loads(udp.transport.sent[-1][0])
            invalid = {
                "type": "pair_proof",
                "device_id": "phone-a",
                "token_id": token.token_id,
                "client_nonce": "nonce-a",
                **challenge,
                "proof": "00" * 32,
            }
            await udp._handle_datagram(
                json.dumps(invalid).encode(), ("127.0.0.1", 54321)
            )
            self.assertEqual(len(udp.transport.sent), 1)

            valid = {
                **invalid,
                "proof": compute_pairing_proof(
                    token.value, token.token_id, "phone-a", "nonce-a",
                    challenge["challenge_id"], challenge["challenge"]
                ),
            }
            await udp._handle_datagram(
                json.dumps(valid).encode(), ("127.0.0.1", 54321)
            )
            self.assertEqual(len(udp.transport.sent), 1)
            await cm.stop()
        asyncio.run(run())

    def test_missing_and_invalid_mac_rejected(self):
        async def run():
            cm, security, emulator, udp = await self._server()
            response, _, _ = await self._pair(udp, security)
            session = {"client_id": "phone-a", "session_id": response["session_id"]}
            missing = {
                "type": "input", "client_id": "phone-a",
                "session_id": session["session_id"], "sequence": 0,
                "data": {"a": True},
            }
            await udp._handle_datagram(json.dumps(missing).encode(), ("127.0.0.1", 54321))
            self.assertIsNone(emulator.last_state)
            await self._control(udp, security, session, mac="00" * 32)
            self.assertIsNone(emulator.last_state)
            await cm.stop()
        asyncio.run(run())

    def test_replay_rejected(self):
        async def run():
            cm, security, emulator, udp = await self._server()
            response, _, _ = await self._pair(udp, security)
            session = {"client_id": "phone-a", "session_id": response["session_id"]}
            await self._control(udp, security, session)
            first_state = emulator.last_state
            await self._control(udp, security, session)
            self.assertIs(emulator.last_state, first_state)
            await cm.stop()
        asyncio.run(run())

    def test_pair_proof_replay_and_legacy_pair_are_rejected(self):
        async def run():
            cm, security, _, udp = await self._server()
            token = security.issue_pairing_token("phone-a")
            addr = ("127.0.0.1", 54321)
            begin = {
                "type": "pair_begin",
                "device_id": "phone-a",
                "token_id": token.token_id,
                "client_nonce": "nonce-a",
            }
            await udp._handle_datagram(json.dumps(begin).encode(), addr)
            challenge = json.loads(udp.transport.sent[-1][0])
            proof = {
                **begin,
                **challenge,
                "type": "pair_proof",
                "proof": compute_pairing_proof(
                    token.value, token.token_id, "phone-a", "nonce-a",
                    challenge["challenge_id"], challenge["challenge"],
                ),
            }
            # The response type must be pair_ack, not another challenge.
            await udp._handle_datagram(json.dumps(proof).encode(), addr)
            self.assertEqual(
                json.loads(udp.transport.sent[-1][0])["type"], "pair_ack"
            )
            sent = len(udp.transport.sent)
            await udp._handle_datagram(json.dumps(proof).encode(), addr)
            await udp._handle_datagram(json.dumps({
                **proof, "type": "pair",
            }).encode(), addr)
            self.assertEqual(len(udp.transport.sent), sent)
            await cm.stop()
        asyncio.run(run())

    def test_expired_challenge_and_invalid_proof_do_not_consume_token(self):
        async def run():
            now = [100.0]
            cm, security, _, udp = await self._server(clock=lambda: now[0])
            token = security.issue_pairing_token("phone-a")
            addr = ("127.0.0.1", 54321)
            begin = {
                "type": "pair_begin", "device_id": "phone-a",
                "token_id": token.token_id, "client_nonce": "nonce-a",
            }
            await udp._handle_datagram(json.dumps(begin).encode(), addr)
            challenge = json.loads(udp.transport.sent[-1][0])
            invalid = {
                **begin, "type": "pair_proof", **challenge, "proof": "00" * 32
            }
            await udp._handle_datagram(json.dumps(invalid).encode(), addr)
            now[0] = 111.0
            await udp._handle_datagram(json.dumps({
                **invalid,
                "proof": compute_pairing_proof(
                    token.value, token.token_id, "phone-a", "nonce-a",
                    challenge["challenge_id"], challenge["challenge"],
                ),
            }).encode(), addr)
            self.assertEqual(len(udp.transport.sent), 1)
            # A fresh challenge proves the invalid proof did not consume token.
            now[0] = 100.0
            await udp._handle_datagram(json.dumps(begin).encode(), addr)
            self.assertEqual(len(udp.transport.sent), 2)
            await cm.stop()
        asyncio.run(run())

    def test_pair_begin_replay_does_not_create_session_and_ack_has_no_secret(self):
        async def run():
            cm, security, _, udp = await self._server()
            token = security.issue_pairing_token("phone-a")
            begin = {
                "type": "pair_begin", "device_id": "phone-a",
                "token_id": token.token_id, "client_nonce": "nonce-a",
            }
            wire = json.dumps(begin).encode()
            addr = ("127.0.0.1", 54321)
            await udp._handle_datagram(wire, addr)
            first = json.loads(udp.transport.sent[-1][0])
            await udp._handle_datagram(wire, addr)
            second = json.loads(udp.transport.sent[-1][0])
            self.assertNotEqual(first["challenge_id"], second["challenge_id"])
            self.assertEqual(cm.get_client_count(), 0)
            response, _, _ = await self._pair(udp, security, "phone-b")
            self.assertNotIn("session_secret", response)
            self.assertNotIn("token", response)
            await cm.stop()
        asyncio.run(run())

    def test_discovery_is_public_but_not_privileged(self):
        async def run():
            cm, security, emulator, udp = await self._server()
            await udp._handle_datagram(
                b'{"type":"discover"}', ("127.0.0.1", 54321)
            )
            self.assertEqual(json.loads(udp.transport.sent[0][0])["type"], "discover_ack")
            self.assertEqual(cm.get_client_count(), 0)
            await udp._handle_datagram(
                b'{"type":"heartbeat","client_id":"phone-a"}',
                ("127.0.0.1", 54321),
            )
            self.assertIsNone(emulator.last_state)
            await cm.stop()
        asyncio.run(run())

    def test_pairing_rejected_when_max_clients_reached(self):
        async def run():
            cm, security, _, udp = await self._server(max_clients=1)
            await self._pair(udp, security, "phone-a")
            sent = len(udp.transport.sent)
            token = security.issue_pairing_token("phone-b")
            addr = ("127.0.0.1", 54321)
            await udp._handle_datagram(json.dumps({
                "type": "pair_begin",
                "device_id": "phone-b",
                "token_id": token.token_id,
                "client_nonce": "nonce-b",
            }).encode(), addr)
            challenge = json.loads(udp.transport.sent[-1][0])
            await udp._handle_datagram(json.dumps({
                "type": "pair_proof",
                "device_id": "phone-b",
                "token_id": token.token_id,
                "client_nonce": "nonce-b",
                "challenge_id": challenge["challenge_id"],
                "challenge": challenge["challenge"],
                "proof": compute_pairing_proof(
                    token.value, token.token_id, "phone-b", "nonce-b",
                    challenge["challenge_id"], challenge["challenge"],
                ),
            }).encode(), addr)
            self.assertEqual(len(udp.transport.sent), sent + 1)
            self.assertEqual(cm.get_client_count(), 1)
            await cm.stop()
        asyncio.run(run())


if __name__ == "__main__":
    unittest.main()
