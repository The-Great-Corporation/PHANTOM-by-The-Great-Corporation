import asyncio
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.connection_manager import ConnectionManager
from core.session_security import SessionSecurity, compute_pairing_proof
from core.session_security import InvalidMacError, SequenceAlreadySeenError, SessionSecurityError
from core.stream_security import StreamAuthenticator
from protocols.usb_server import USBServer
from protocols.websocket_server import WebSocketServer


class MockEmulator:
    async def update_state(self, state, client_id=None):
        pass


class FakeWebSocket:
    def __init__(self, messages=None, *, token=None, device_id=None, nonce=None):
        self.messages = list(messages or [])
        self.sent = []
        self.remote_address = ("127.0.0.1", 45000)
        self.closed = None
        self._token = token
        self._device_id = device_id
        self._nonce = nonce
        self._challenge = None
        self._proof_sent = False

    def __aiter__(self):
        return self

    async def __anext__(self):
        if self.messages:
            return self.messages.pop(0)
        if self._challenge is not None and not self._proof_sent:
            self._proof_sent = True
            proof = compute_pairing_proof(
                self._token.value,
                self._token.token_id,
                self._device_id,
                self._nonce,
                self._challenge["challenge_id"],
                self._challenge["challenge"],
            )
            return json.dumps({
                "type": "pair_proof",
                "device_id": self._device_id,
                "token_id": self._token.token_id,
                "client_nonce": self._nonce,
                "challenge_id": self._challenge["challenge_id"],
                "challenge": self._challenge["challenge"],
                "proof": proof,
            })
        raise StopAsyncIteration

    async def send(self, payload):
        self.sent.append(payload)
        parsed = json.loads(payload)
        if parsed.get("type") == "pair_challenge":
            self._challenge = parsed

    async def close(self, code=1000, reason=""):
        self.closed = (code, reason)


class FakeReader:
    def __init__(self):
        self.buffer = []

    async def read(self, size):
        if not self.buffer:
            return b""
        chunk = self.buffer.pop(0)
        return chunk


class FakeWriter:
    def __init__(self):
        self.buffer = []
        self.closed = False

    def get_extra_info(self, name):
        if name == "peername":
            return ("127.0.0.1", 45001)
        return None

    def write(self, data):
        self.buffer.append(data)

    async def drain(self):
        return None

    def close(self):
        self.closed = True

    async def wait_closed(self):
        return None


class TestStreamAuth(unittest.TestCase):
    def test_stream_messages_require_valid_mac_and_reject_replay(self):
        security = SessionSecurity()
        token = security.issue_pairing_token("phone-stream")
        challenge = security.create_pairing_challenge(
            token.token_id, "phone-stream", "nonce-stream", ("127.0.0.1", 1)
        )
        session = security.consume_pairing_challenge(
            challenge.challenge_id,
            challenge.challenge,
            token.token_id,
            "phone-stream",
            "nonce-stream",
            compute_pairing_proof(
                token.value,
                token.token_id,
                "phone-stream",
                "nonce-stream",
                challenge.challenge_id,
                challenge.challenge,
            ),
            ("127.0.0.1", 1),
        )
        authenticator = StreamAuthenticator(security)
        payload = {"a": True}
        envelope = {
            "type": "input",
            "client_id": "phone-stream",
            "session_id": session.identity.session_id,
            "sequence": 1,
            "data": payload,
        }
        envelope["mac"] = security.compute_mac(
            session.identity.session_id,
            1,
            {
                "client_id": "phone-stream",
                "session_id": session.identity.session_id,
                "payload": payload,
            },
        ).hex()
        self.assertEqual(authenticator.verify_message(session, envelope), payload)
        with self.assertRaises(SequenceAlreadySeenError):
            authenticator.verify_message(session, envelope)

        unsigned = dict(envelope)
        unsigned.pop("mac")
        with self.assertRaises(SessionSecurityError):
            authenticator.verify_message(session, unsigned)

        invalid_mac = dict(envelope)
        invalid_mac["sequence"] = 2
        invalid_mac["mac"] = "00" * 32
        with self.assertRaises(InvalidMacError):
            authenticator.verify_message(session, invalid_mac)

    def test_websocket_requires_pair_proof_before_connecting(self):
        async def run():
            cm = ConnectionManager({"connection": {"max_clients": 2}})
            await cm.start()
            security = SessionSecurity()
            server = WebSocketServer("127.0.0.1", 8889, cm, MockEmulator(), object(), security=security)
            token = security.issue_pairing_token("phone-a")
            nonce = "nonce-a"
            connected = {"value": None}

            async def fake_connect_client(client_id, protocol, address):
                connected["value"] = (client_id, protocol, address)
                return True

            server.connection_manager.connect_client = fake_connect_client

            ws = FakeWebSocket([
                json.dumps({
                    "type": "connect",
                    "device_id": "phone-a",
                    "token_id": token.token_id,
                    "client_nonce": nonce,
                })
            ], token=token, device_id="phone-a", nonce=nonce)
            await server._handle_client(ws)
            self.assertIsNotNone(connected["value"])
            self.assertEqual(connected["value"][0], "phone-a")
            await cm.stop()

        asyncio.run(run())

    def test_usb_rejects_input_before_authentication(self):
        async def run():
            cm = ConnectionManager({"connection": {"max_clients": 2}})
            await cm.start()
            security = SessionSecurity()

            class ADBStub:
                async def initialize(self):
                    return None

                def is_available(self):
                    return False

                async def setup_port_forwarding(self):
                    return None

                async def remove_port_forwarding(self):
                    return None

            server = USBServer(8890, cm, MockEmulator(), object(), ADBStub(), security=security)
            token = security.issue_pairing_token("phone-b")
            nonce = "nonce-b"
            writer = FakeWriter()
            await server._handle_message(
                json.dumps({
                    "type": "connect",
                    "device_id": "phone-b",
                    "token_id": token.token_id,
                    "client_nonce": nonce,
                }),
                FakeReader(),
                writer,
            )
            self.assertIsNone(cm.get_client("phone-b"))
            challenge = json.loads(writer.buffer[0].decode("utf-8"))
            proof = compute_pairing_proof(
                token.value,
                token.token_id,
                "phone-b",
                nonce,
                challenge["challenge_id"],
                challenge["challenge"],
            )
            await server._handle_message(
                json.dumps({
                    "type": "pair_proof",
                    "device_id": "phone-b",
                    "token_id": token.token_id,
                    "client_nonce": nonce,
                    "challenge_id": challenge["challenge_id"],
                    "challenge": challenge["challenge"],
                    "proof": proof,
                }),
                FakeReader(),
                writer,
            )
            self.assertIsNotNone(cm.get_client("phone-b"))
            await cm.stop()

        asyncio.run(run())


if __name__ == "__main__":
    unittest.main()
