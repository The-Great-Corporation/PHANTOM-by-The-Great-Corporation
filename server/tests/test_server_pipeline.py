import sys
import os
import unittest
import asyncio
import json
import socket
import time

# Add server directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.connection_manager import ConnectionManager
from core.gamepad_emulator import GamepadEmulator, GamepadState
from protocols.udp_server import UDPServer
from core.haptic_feedback import HapticFeedbackManager
from core.session_security import (
    SessionSecurity,
    canonicalize_message,
    compute_pairing_proof,
    derive_session_secret,
)

class TestServerPipeline(unittest.TestCase):
    def setUp(self):
        self.config = {
            "server": {"host": "127.0.0.1", "udp_port": 18888, "websocket_port": 18889, "usb_port": 18890},
            "gamepad": {"emulation_type": "xbox360", "vibration_enabled": True, "deadzone_left": 0.1, "deadzone_right": 0.1},
            "connection": {"max_clients": 2, "timeout": 10}
        }

    def test_connection_manager_lifecycle(self):
        async def run():
            cm = ConnectionManager(self.config)
            await cm.start()
            
            ok = await cm.connect_client("client_test_1", "udp", "127.0.0.1:50000")
            self.assertTrue(ok)
            self.assertEqual(cm.get_client_count(), 1)
            
            await cm.handle_input("client_test_1", {"a": True, "left_stick_x": 0.5})
            client = cm.get_client("client_test_1")
            self.assertIsNotNone(client)
            
            await cm.disconnect_client("client_test_1")
            self.assertEqual(cm.get_client_count(), 0)
            await cm.stop()

        asyncio.run(run())

    def test_gamepad_state(self):
        state = GamepadState(a=True, b=False, left_stick_x=0.75, left_stick_y=-0.25)
        self.assertTrue(state.a)
        self.assertFalse(state.b)
        self.assertEqual(state.left_stick_x, 0.75)
        self.assertEqual(state.left_stick_y, -0.25)

    def test_gamepad_values_are_clamped_before_native_emulation(self):
        self.assertEqual(GamepadEmulator._clamp_axis(2.0, -1.0, 1.0), 1.0)
        self.assertEqual(GamepadEmulator._clamp_axis(-2.0, -1.0, 1.0), -1.0)
        self.assertEqual(GamepadEmulator._clamp_axis(float("nan"), -1.0, 1.0), 0.0)
        self.assertEqual(GamepadEmulator._clamp_axis("invalid", -1.0, 1.0), 0.0)

    def test_udp_rejected_client_cannot_send_input(self):
        async def run():
            config = self.config | {"connection": {"max_clients": 1, "timeout": 10}}
            cm = ConnectionManager(config)
            await cm.start()
            await cm.connect_client("existing", "udp", "127.0.0.1:1")

            class MockEmulator:
                def __init__(self):
                    self.last_state = None
                async def update_state(self, state):
                    self.last_state = state

            udp = UDPServer("127.0.0.1", 8888, cm, MockEmulator(), object())
            message = json.dumps({
                "type": "input", "client_id": "rejected", "data": {"a": True}
            }).encode("utf-8")
            await udp._handle_datagram(message, ("127.0.0.1", 54321))
            self.assertIsNone(udp.gamepad_emulator.last_state)
            await cm.stop()

        asyncio.run(run())

    def test_udp_server_discover_and_input(self):
        async def run():
            cm = ConnectionManager(self.config)
            await cm.start()
            
            class MockTransport:
                def __init__(self):
                    self.sent = []
                def sendto(self, data, addr):
                    self.sent.append((data, addr))
            
            class MockEmulator:
                def __init__(self):
                    self.last_state = None
                async def update_state(self, state):
                    self.last_state = state

            class MockHaptics:
                pass

            security = SessionSecurity()
            udp = UDPServer(
                "127.0.0.1", 8888, cm, MockEmulator(), MockHaptics(), security=security
            )
            mock_transport = MockTransport()
            udp.transport = mock_transport

            # Test discover probe
            discover_data = json.dumps({"type": "discover"}).encode("utf-8")
            await udp._handle_datagram(discover_data, ("127.0.0.1", 54321))
            self.assertEqual(len(mock_transport.sent), 1)
            response_json = json.loads(mock_transport.sent[0][0].decode("utf-8"))
            self.assertEqual(response_json["type"], "discover_ack")
            self.assertEqual(response_json["server_name"], "Phantom by The Great Corporation")

            # Pair before sending a control datagram.
            token = security.issue_pairing_token("test_client_udp")
            pair_begin = {
                "type": "pair_begin",
                "device_id": "test_client_udp",
                "token_id": token.token_id,
                "client_nonce": "nonce-1",
            }
            addr = ("127.0.0.1", 54321)
            await udp._handle_datagram(
                json.dumps(pair_begin).encode("utf-8"), addr
            )
            challenge_response = json.loads(mock_transport.sent[-1][0])
            await udp._handle_datagram(json.dumps({
                **pair_begin,
                **challenge_response,
                "type": "pair_proof",
                "proof": compute_pairing_proof(
                    token.value,
                    token.token_id,
                    "test_client_udp",
                    "nonce-1",
                    challenge_response["challenge_id"],
                    challenge_response["challenge"],
                ),
            }).encode("utf-8"), addr)
            pair_response = json.loads(mock_transport.sent[-1][0])
            session_id = pair_response["session_id"]

            # Test authenticated input datagram.
            payload = {"a": True, "left_stick_x": 0.8}
            unsigned = {
                "type": "input",
                "client_id": "test_client_udp",
                "session_id": session_id,
                "payload": payload,
            }
            client_secret = derive_session_secret(
                token.value,
                "nonce-1",
                pair_response["server_challenge"],
                session_id,
            )
            import hashlib
            import hmac
            mac = hmac.new(
                client_secret,
                canonicalize_message(0, unsigned),
                hashlib.sha256,
            ).hexdigest()
            input_data = json.dumps({
                "type": "input",
                "client_id": "test_client_udp",
                "session_id": session_id,
                "sequence": 0,
                "mac": mac,
                "data": payload,
            }).encode("utf-8")
            await udp._handle_datagram(input_data, ("127.0.0.1", 54321))
            self.assertEqual(udp.gamepad_emulator.last_state.a, True)
            self.assertEqual(udp.gamepad_emulator.last_state.left_stick_x, 0.8)

            await cm.stop()

        asyncio.run(run())

if __name__ == '__main__':
    unittest.main()
