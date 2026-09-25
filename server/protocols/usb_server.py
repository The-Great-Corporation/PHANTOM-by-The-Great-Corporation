"""
USB Server - ADB bridge for zero-latency USB connection
"""

import asyncio
import logging
import json
from typing import Optional
from core.session_security import SessionSecurity, SessionSecurityError
from core.stream_security import StreamAuthenticator

logger = logging.getLogger(__name__)
MAX_MESSAGE_SIZE = 8 * 1024


class USBServer:
    """USB server via ADB bridge for zero-latency connection."""
    
    def __init__(self, port: int, connection_manager, gamepad_emulator, haptic_feedback, adb_bridge, security=None):
        self.port = port
        self.connection_manager = connection_manager
        self.gamepad_emulator = gamepad_emulator
        self.haptic_feedback = haptic_feedback
        self.adb_bridge = adb_bridge
        self.server: Optional[asyncio.Server] = None
        self._running = False
        self._client_sockets = {}  # client_id -> (reader, writer)
        self.authenticator = StreamAuthenticator(security or SessionSecurity())
        self._pending_auth = set()
        self._sessions = {}
        
    async def start(self):
        """Start the USB server."""
        logger.info(f"Starting USB server on port {self.port}")
        
        # Initialize ADB bridge
        await self.adb_bridge.initialize()
        
        # Set up ADB port forwarding
        if self.adb_bridge.is_available():
            await self.adb_bridge.setup_port_forwarding()
        else:
            logger.warning("ADB not available, USB server may not work")
        
        self._running = True
        
        try:
            self.server = await asyncio.start_server(
                self._handle_client,
                '127.0.0.1',
                self.port
            )
            
            logger.info(f"USB server listening on 127.0.0.1:{self.port}")
            
            async with self.server:
                while self._running:
                    await asyncio.sleep(1)
                    
        except Exception as e:
            logger.error(f"Failed to start USB server: {e}")
            raise
    
    async def stop(self):
        """Stop the USB server."""
        logger.info("Stopping USB server")
        self._running = False
        
        # Close all client connections
        for client_id, (reader, writer) in self._client_sockets.items():
            try:
                writer.close()
                await writer.wait_closed()
            except Exception:
                pass
        
        self._client_sockets.clear()
        
        # Close server
        if self.server:
            self.server.close()
            await self.server.wait_closed()
        
        # Remove ADB port forwarding
        if self.adb_bridge.is_available():
            await self.adb_bridge.remove_port_forwarding()
    
    async def _handle_client(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        """Handle a USB client connection."""
        client_id = None
        address = writer.get_extra_info('peername')
        
        try:
            logger.info(f"New USB connection from {address}")
            
            buffer = ""
            
            while self._running:
                try:
                    data = await reader.read(1024)
                    if not data:
                        break
                    
                    buffer += data.decode('utf-8')
                    if len(buffer.encode('utf-8')) > MAX_MESSAGE_SIZE:
                        logger.warning("Closing USB connection with oversized message from %s", address)
                        break
                    
                    # Process complete messages
                    while '\n' in buffer:
                        line, buffer = buffer.split('\n', 1)
                        if line.strip():
                            connected_id = await self._handle_message(line.strip(), reader, writer)
                            client_id = connected_id or client_id
                            
                except asyncio.CancelledError:
                    break
                except Exception as e:
                    logger.error(f"Error receiving from USB client: {e}")
                    await asyncio.sleep(0.1)
                    
        except Exception as e:
            logger.error(f"USB client error: {e}")
        finally:
            if client_id:
                await self.connection_manager.disconnect_client(client_id)
                if client_id in self._client_sockets:
                    del self._client_sockets[client_id]
                self._sessions.pop(writer, None)
            try:
                writer.close()
                await writer.wait_closed()
            except Exception:
                pass
            logger.info(f"USB client {client_id} disconnected")
    
    async def _handle_message(self, message: str, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        """Handle a message from USB client."""
        try:
            data = json.loads(message)
            msg_type = data.get('type')
            client_id = data.get('device_id') or data.get('client_id')
            
            if not client_id:
                logger.warning("Received message without client_id")
                return None
            
            if msg_type == 'connect':
                response = self.authenticator.begin(data, writer.get_extra_info('peername'))
                self._pending_auth.add(writer)
                writer.write((json.dumps(response) + '\n').encode('utf-8'))
                await writer.drain()
                return None

            if msg_type == 'reconnect_begin':
                response = self.authenticator.begin_reconnect(data, writer.get_extra_info('peername'))
                self._pending_auth.add(writer)
                writer.write((json.dumps(response) + '\n').encode('utf-8'))
                await writer.drain()
                return None
            
            if msg_type == 'pair_proof':
                if writer not in self._pending_auth:
                    raise SessionSecurityError("authentication handshake not started")
                session = self.authenticator.complete(data, writer.get_extra_info('peername'))
                client_id = session.identity.device_id
                accepted = await self.connection_manager.connect_client(
                    client_id,
                    'usb',
                    writer.get_extra_info('peername')[0]
                )
                if not accepted:
                    return None
                self._client_sockets[client_id] = (reader, writer)
                self._sessions[writer] = session

                # Send connection confirmation
                response = json.dumps({
                    'type': 'connected',
                    'client_id': client_id,
                    'session_id': session.identity.session_id,
                    'server_challenge': data.get('challenge'),
                })
                writer.write((response + '\n').encode('utf-8'))
                await writer.drain()
                self._pending_auth.discard(writer)
                return client_id

            if msg_type == 'reconnect_proof':
                if writer not in self._pending_auth:
                    raise SessionSecurityError("authentication handshake not started")
                session = self.authenticator.complete_reconnect(data, writer.get_extra_info('peername'))
                client_id = session.identity.device_id
                accepted = await self.connection_manager.connect_client(
                    client_id,
                    'usb',
                    writer.get_extra_info('peername')[0]
                )
                if not accepted:
                    return None
                self._client_sockets[client_id] = (reader, writer)
                self._sessions[writer] = session
                
                # Send connection confirmation
                response = json.dumps({
                    'type': 'connected',
                    'client_id': client_id,
                    'session_id': session.identity.session_id,
                    'server_challenge': data.get('challenge'),
                })
                writer.write((response + '\n').encode('utf-8'))
                await writer.drain()
                self._pending_auth.discard(writer)
                return client_id
            
            elif msg_type in {'input', 'heartbeat', 'ping'}:
                if writer in self._pending_auth:
                    raise SessionSecurityError("authentication required")
                if self._client_sockets.get(client_id, (None, None))[1] is not writer:
                    raise SessionSecurityError("authentication required")
                session = self._sessions.get(writer)
                if session is None:
                    raise SessionSecurityError("authentication required")
                if msg_type == 'input':
                    await self._handle_input(
                        client_id, self.authenticator.verify_message(session, data)
                    )
                elif msg_type == 'heartbeat':
                    self.authenticator.verify_message(session, data)
                    await self.connection_manager.update_activity(client_id)
                elif msg_type == 'ping':
                    self.authenticator.verify_message(session, data)
                    await self._send_pong(client_id, writer)
            else:
                raise SessionSecurityError("unsupported USB message type")
                
        except (SessionSecurityError, ValueError, TypeError) as e:
            logger.warning("Rejected USB authentication: %s", e)
            writer.close()
            return None
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON received: {e}")
        except Exception as e:
            logger.error(f"Error handling message: {e}")
    
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
            
            await self.gamepad_emulator.update_state(state)
            await self.connection_manager.handle_input(client_id, input_data)
            
        except Exception as e:
            logger.error(f"Error handling input: {e}")
    
    async def _send_pong(self, client_id: str, writer: asyncio.StreamWriter):
        """Send pong response to ping."""
        try:
            response = json.dumps({
                'type': 'pong',
                'client_id': client_id,
                'timestamp': asyncio.get_event_loop().time()
            })
            writer.write((response + '\n').encode('utf-8'))
            await writer.drain()
        except Exception as e:
            logger.error(f"Error sending pong: {e}")
    
    async def send_haptic_feedback(self, client_id: str, intensity: float, duration: float):
        """Send haptic feedback to client."""
        if client_id not in self._client_sockets:
            return
        
        reader, writer = self._client_sockets[client_id]
        
        try:
            message = json.dumps({
                'type': 'haptic',
                'intensity': intensity,
                'duration': duration
            })
            writer.write((message + '\n').encode('utf-8'))
            await writer.drain()
        except Exception as e:
            logger.error(f"Error sending haptic feedback: {e}")
