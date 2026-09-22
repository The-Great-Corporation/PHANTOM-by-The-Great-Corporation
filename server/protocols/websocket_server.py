"""
WebSocket Server - Reliable WebSocket protocol for fallback connection
"""

import asyncio
import logging
import json
from websockets.server import serve
from typing import Dict, Set
from core.session_security import SessionSecurity, SessionSecurityError
from core.stream_security import StreamAuthenticator

logger = logging.getLogger(__name__)


class WebSocketServer:
    """WebSocket server for reliable gamepad input."""
    
    def __init__(self, host: str, port: int, connection_manager, gamepad_emulator, haptic_feedback, security=None):
        self.host = host
        self.port = port
        self.connection_manager = connection_manager
        self.gamepad_emulator = gamepad_emulator
        self.haptic_feedback = haptic_feedback
        self.clients: Dict[str, object] = {}  # client_id -> websocket
        self._running = False
        self._server = None
        self.max_message_size = 8 * 1024
        self.authenticator = StreamAuthenticator(security or SessionSecurity())
        
        if hasattr(self.haptic_feedback, 'register_sender'):
            self.haptic_feedback.register_sender(self._send_vibration_to_client)
        
    async def start(self):
        """Start the WebSocket server."""
        logger.info(f"Starting WebSocket server on {self.host}:{self.port}")
        
        self._running = True
        
        try:
            self._server = await serve(
                self._handle_client,
                self.host,
                self.port,
                max_size=self.max_message_size,
            )
            logger.info(f"WebSocket server listening on {self.host}:{self.port}")
            
            # Keep the server running
            while self._running:
                await asyncio.sleep(1)
                
        except Exception as e:
            logger.error(f"Failed to start WebSocket server: {e}")
            raise
    
    async def stop(self):
        """Stop the WebSocket server."""
        logger.info("Stopping WebSocket server")
        self._running = False
        
        if self._server:
            self._server.close()
            await self._server.wait_closed()
        
        # Close all client connections
        for client_id, websocket in self.clients.items():
            try:
                await websocket.close()
            except Exception:
                pass
        
        self.clients.clear()
    
    async def _handle_client(self, websocket, path=None):
        """Handle a WebSocket client connection."""
        client_id = None
        authenticated = False
        address = websocket.remote_address
        session = None
        
        try:
            logger.info(f"New WebSocket connection from {websocket.remote_address}")
            
            async for message in websocket:
                try:
                    data = json.loads(message)
                    msg_type = data.get('type')
                    
                    if msg_type == 'connect' and not authenticated:
                        client_id = data.get('device_id') or data.get('client_id')
                        await websocket.send(json.dumps(
                            self.authenticator.begin(data, address)
                        ))
                    elif msg_type == 'pair_proof' and not authenticated:
                        session = self.authenticator.complete(data, address)
                        client_id = session.identity.device_id
                        accepted = await self.connection_manager.connect_client(
                            client_id, 'websocket', str(address)
                        )
                        if not accepted:
                            await websocket.close(code=1008, reason='Maximum clients reached')
                            return
                        authenticated = True
                        self.clients[client_id] = websocket
                        await websocket.send(json.dumps({
                            'type': 'connected', 'client_id': client_id,
                            'session_id': session.identity.session_id,
                            'server_challenge': data.get('challenge'),
                        }))
                    elif msg_type == 'input' and authenticated:
                        if client_id and self.clients.get(client_id) is websocket:
                            await self._handle_input(
                                client_id, self.authenticator.verify_message(session, data)
                            )
                    
                    elif msg_type == 'heartbeat' and authenticated:
                        if client_id:
                            self.authenticator.verify_message(session, data)
                            await self.connection_manager.update_activity(client_id)
                    
                    elif msg_type == 'ping' and authenticated:
                        if client_id:
                            self.authenticator.verify_message(session, data)
                            await websocket.send(json.dumps({
                                'type': 'pong',
                                'client_id': client_id,
                                'timestamp': asyncio.get_event_loop().time()
                            }))
                    elif msg_type in ('input', 'heartbeat', 'ping'):
                        await websocket.close(code=1008, reason='Authentication required')
                        return
                            
                except json.JSONDecodeError as e:
                    logger.error(f"Invalid JSON received: {e}")
                except (SessionSecurityError, ValueError, TypeError) as e:
                    logger.warning("Rejected WebSocket authentication: %s", e)
                    await websocket.close(code=1008, reason='Authentication failed')
                    return
                except Exception as e:
                    logger.error(f"Error handling message: {e}")
                    
        except Exception as e:
            logger.error(f"WebSocket client error: {e}")
        finally:
            if client_id:
                await self.connection_manager.disconnect_client(client_id)
                if client_id in self.clients:
                    del self.clients[client_id]
            logger.info(f"WebSocket client {client_id} disconnected")
    
    async def _handle_input(self, client_id: str, input_data: dict):
        """Handle gamepad input data."""
        try:
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
            
            try:
                await self.gamepad_emulator.update_state(state, client_id=client_id)
            except TypeError:
                await self.gamepad_emulator.update_state(state)
            await self.connection_manager.handle_input(client_id, input_data)
            
        except Exception as e:
            logger.error(f"Error handling input: {e}")
    
    async def _send_vibration_to_client(self, client_id: str, left: float, right: float, duration: float):
        """Send vibration payload to WebSocket client."""
        if client_id not in self.clients:
            return
        websocket = self.clients[client_id]
        try:
            message = json.dumps({
                'type': 'vibration',
                'left': left,
                'right': right,
                'duration': duration
            })
            await websocket.send(message)
        except Exception as e:
            logger.error(f"Error sending vibration over websocket: {e}")

    async def send_haptic_feedback(self, client_id: str, intensity: float, duration: float):
        """Send haptic feedback to client."""
        if client_id not in self.clients:
            return
        
        websocket = self.clients[client_id]
        
        try:
            message = json.dumps({
                'type': 'haptic',
                'intensity': intensity,
                'duration': duration
            })
            await websocket.send(message)
        except Exception as e:
            logger.error(f"Error sending haptic feedback: {e}")
