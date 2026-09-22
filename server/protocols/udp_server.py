"""
UDP Server - High-performance UDP protocol for local Wi-Fi connection
"""

import asyncio
import logging
import json
from typing import Dict, Optional

from core.session_security import (
    InvalidSequenceError,
    SessionSecurity,
    SessionSecurityError,
)

logger = logging.getLogger(__name__)


class UDPServer:
    """UDP server for low-latency gamepad input."""
    
    def __init__(
        self,
        host: str,
        port: int,
        connection_manager,
        gamepad_emulator,
        haptic_feedback,
        security: Optional[SessionSecurity] = None,
    ):
        self.host = host
        self.port = port
        self.connection_manager = connection_manager
        self.gamepad_emulator = gamepad_emulator
        self.haptic_feedback = haptic_feedback
        self.server: Optional[asyncio.DatagramProtocol] = None
        self.transport: Optional[asyncio.DatagramTransport] = None
        self._running = False
        self.max_datagram_size = 8 * 1024
        self.client_addrs: Dict[str, tuple] = {}
        self.security = security if security is not None else SessionSecurity()
        self._sessions: Dict[str, str] = {}
        
        if hasattr(self.haptic_feedback, 'register_sender'):
            self.haptic_feedback.register_sender(self._send_vibration_to_client)
        
    async def start(self):
        """Start the UDP server."""
        logger.info(f"Starting UDP server on {self.host}:{self.port}")
        
        loop = asyncio.get_event_loop()
        
        class UDPProtocol(asyncio.DatagramProtocol):
            def __init__(self, server):
                self.server = server
                
            def connection_made(self, transport):
                self.server.transport = transport
                logger.info("UDP server connection made")
                
            def datagram_received(self, data, addr):
                asyncio.create_task(self.server._handle_datagram(data, addr))
                
            def error_received(self, exc):
                logger.error(f"UDP error: {exc}")
                
            def connection_lost(self, exc):
                logger.info("UDP server connection lost")
        
        try:
            self.transport, protocol = await loop.create_datagram_endpoint(
                lambda: UDPProtocol(self),
                local_addr=(self.host, self.port)
            )
            self._running = True
            logger.info(f"UDP server listening on {self.host}:{self.port}")
            
            # Keep the server running
            while self._running:
                await asyncio.sleep(1)
                
        except Exception as e:
            logger.error(f"Failed to start UDP server: {e}")
            raise
    
    async def stop(self):
        """Stop the UDP server."""
        logger.info("Stopping UDP server")
        self._running = False
        
        if self.transport:
            self.transport.close()
            try:
                await asyncio.wait_for(self.transport.wait_closed(), timeout=2.0)
            except asyncio.TimeoutError:
                pass
    
    async def _handle_datagram(self, data, addr):
        """Handle incoming UDP datagram."""
        try:
            if len(data) > self.max_datagram_size:
                logger.warning("Discarding oversized UDP datagram from %s", addr[0])
                return
            # Parse JSON data
            message = json.loads(data.decode('utf-8'))
            msg_type = message.get('type')
            
            if msg_type == 'discover':
                # Client scanning for available TGC servers
                response = json.dumps({
                    'type': 'discover_ack',
                    'server_name': 'Phantom by The Great Corporation',
                    'port': self.port,
                    'version': '2.0'
                }).encode('utf-8')
                self.transport.sendto(response, addr)
                logger.info(f"Auto-Discovery probe received from {addr[0]}, response sent")
                return

            if msg_type == "pair_begin":
                await self._handle_pair_begin(message, addr)
                return

            if msg_type == "pair_proof":
                await self._handle_pair_proof(message, addr)
                return

            if msg_type == "pair":
                logger.warning("Rejected legacy UDP pair message from %s", addr[0])
                return

            if not isinstance(message, dict):
                logger.warning("Discarding non-object UDP message")
                return

            client_id = self._authenticate_control_message(message)
            self.client_addrs[client_id] = addr

            if msg_type == 'input':
                await self._handle_input(client_id, message.get('data', {}))
            elif msg_type == 'heartbeat':
                await self.connection_manager.update_activity(client_id)
            elif msg_type == 'ping':
                await self._send_pong(client_id, addr)
            else:
                logger.warning("Discarding unknown authenticated UDP message type")
        except SessionSecurityError as exc:
            logger.warning("Rejected UDP security request: %s", exc)
        except (TypeError, ValueError, UnicodeDecodeError) as exc:
            logger.warning("Rejected malformed UDP message: %s", exc)
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON received: {e}")
        except Exception as e:
            logger.error(f"Error handling datagram: {e}")

    async def _handle_pair_begin(self, message: dict, addr):
        """Issue a short-lived challenge without consuming a pairing token."""
        device_id = message.get("device_id")
        token_id = message.get("token_id")
        client_nonce = message.get("client_nonce")
        if (
            not isinstance(device_id, str)
            or not device_id
            or not isinstance(token_id, str)
            or not token_id
            or not isinstance(client_nonce, str)
            or not client_nonce
        ):
            raise ValueError("pair_begin requires device_id, token_id and client_nonce")

        challenge = self.security.create_pairing_challenge(
            token_id, device_id, client_nonce, addr
        )
        response = {
            "type": "pair_challenge",
            "challenge_id": challenge.challenge_id,
            "challenge": challenge.challenge,
        }
        if self.transport:
            self.transport.sendto(json.dumps(response).encode("utf-8"), addr)

    async def _handle_pair_proof(self, message: dict, addr):
        """Verify a challenge-bound proof and establish one UDP session."""
        device_id = message.get("device_id")
        token_id = message.get("token_id")
        client_nonce = message.get("client_nonce")
        challenge_id = message.get("challenge_id")
        challenge = message.get("challenge")
        proof = message.get("proof")
        if not all(
            isinstance(value, str) and value
            for value in (
                device_id,
                token_id,
                client_nonce,
                challenge_id,
                challenge,
                proof,
            )
        ):
            raise ValueError(
                "pair_proof requires device_id, token_id, client_nonce, "
                "challenge_id, challenge and proof"
            )

        if (
            self.connection_manager.get_client(device_id) is None
            and self.connection_manager.get_client_count()
            >= self.connection_manager.max_clients
        ):
            logger.warning("Rejected UDP pairing because max_clients is reached")
            return

        session = self.security.consume_pairing_challenge(
            challenge_id,
            challenge,
            token_id,
            device_id,
            client_nonce,
            proof,
            addr,
        )
        accepted = await self.connection_manager.connect_client(
            device_id, "udp", f"{addr[0]}:{addr[1]}"
        )
        if not accepted:
            logger.warning("Rejected UDP pairing because max_clients is reached")
            return

        session_id = session.identity.session_id
        self._sessions[session_id] = device_id
        self.client_addrs[device_id] = addr
        response = {
            "type": "pair_ack",
            "confirmation": "paired",
            "session_id": session_id,
            "server_challenge": challenge,
        }
        if self.transport:
            self.transport.sendto(json.dumps(response).encode("utf-8"), addr)

    def _authenticate_control_message(self, message: dict) -> str:
        """Verify a control datagram before allowing any state mutation."""
        msg_type = message.get("type")
        client_id = message.get("client_id")
        session_id = message.get("session_id")
        sequence = message.get("sequence")
        mac_value = message.get("mac")

        if not isinstance(client_id, str) or not client_id:
            raise ValueError("control message requires client_id")
        if not isinstance(session_id, str) or self._sessions.get(session_id) != client_id:
            raise SessionSecurityError("unknown UDP session")
        if not isinstance(sequence, int) or isinstance(sequence, bool) or sequence < 0:
            raise InvalidSequenceError("sequence must be a non-negative integer")
        if not isinstance(mac_value, str) or len(mac_value) != 64:
            raise SessionSecurityError("control message requires a MAC")
        try:
            mac = bytes.fromhex(mac_value)
        except ValueError as exc:
            raise SessionSecurityError("control message MAC is malformed") from exc

        authenticated_message = {
            "type": msg_type,
            "client_id": client_id,
            "session_id": session_id,
            "payload": message.get("data", {}),
        }
        self.security.verify_mac(session_id, sequence, authenticated_message, mac)
        return client_id
    
    async def _handle_input(self, client_id: str, input_data: dict):
        """Handle gamepad input data."""
        try:
            # Convert input data to GamepadState
            from core.gamepad_emulator import GamepadState
            
            state = GamepadState(
                a=input_data.get('a', False),
                b=input_data.get('b', False),
                x=input_data.get('x', False),
                y=input_data.get('y', False),
                left_bumper=input_data.get('left_bumper', False),
                right_bumper=input_data.get('right_bumper', False),
                left_trigger=input_data.get('left_trigger', 0.0),
                right_trigger=input_data.get('right_trigger', 0.0),
                back=input_data.get('back', False),
                start=input_data.get('start', False),
                left_thumb=input_data.get('left_thumb', False),
                right_thumb=input_data.get('right_thumb', False),
                dpad_up=input_data.get('dpad_up', False),
                dpad_down=input_data.get('dpad_down', False),
                dpad_left=input_data.get('dpad_left', False),
                dpad_right=input_data.get('dpad_right', False),
                left_stick_x=input_data.get('left_stick_x', 0.0),
                left_stick_y=input_data.get('left_stick_y', 0.0),
                right_stick_x=input_data.get('right_stick_x', 0.0),
                right_stick_y=input_data.get('right_stick_y', 0.0),
            )
            
            # Update gamepad state with client_id (backward-compatible)
            try:
                await self.gamepad_emulator.update_state(state, client_id=client_id)
            except TypeError:
                await self.gamepad_emulator.update_state(state)
            
            # Update connection manager
            await self.connection_manager.handle_input(client_id, input_data)
            
        except Exception as e:
            logger.error(f"Error handling input: {e}")

    def _send_vibration_to_client(self, client_id: str, left: float, right: float, duration: float):
        """Send vibration payload to UDP client."""
        addr = self.client_addrs.get(client_id)
        if addr and self.transport:
            try:
                payload = json.dumps({
                    'type': 'vibration',
                    'left': left,
                    'right': right,
                    'duration': duration
                }).encode('utf-8')
                self.transport.sendto(payload, addr)
            except Exception as e:
                logger.error(f"Error sending UDP vibration to {client_id}: {e}")
    
    async def _send_pong(self, client_id: str, addr):
        """Send pong response to ping."""
        if self.transport:
            response = json.dumps({
                'type': 'pong',
                'client_id': client_id,
                'timestamp': asyncio.get_event_loop().time()
            }).encode('utf-8')
            self.transport.sendto(response, addr)
    
    async def send_haptic_feedback(self, client_id: str, intensity: float, duration: float):
        """Send haptic feedback to client."""
        # Find client address from connection manager
        client = self.connection_manager.get_client(client_id)
        if not client:
            return
        
        if self.transport:
            try:
                addr = tuple(client.address.split(':'))
                addr = (addr[0], int(addr[1]))
                
                message = json.dumps({
                    'type': 'haptic',
                    'intensity': intensity,
                    'duration': duration
                }).encode('utf-8')
                
                self.transport.sendto(message, addr)
            except Exception as e:
                logger.error(f"Error sending haptic feedback: {e}")
