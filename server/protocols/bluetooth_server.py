"""
Bluetooth Server - RFCOMM Bluetooth server for local connection
"""

import asyncio
import logging
import json
from typing import Optional
from core.session_security import SessionSecurity, SessionSecurityError
from core.stream_security import StreamAuthenticator

try:
    import bluetooth
except ImportError:
    bluetooth = None
    logging.warning("pybluez not installed. Bluetooth server will not work.")

logger = logging.getLogger(__name__)
MAX_MESSAGE_SIZE = 8 * 1024


class BluetoothServer:
    """Bluetooth RFCOMM server for gamepad input."""
    
    def __init__(self, port: int, connection_manager, gamepad_emulator, haptic_feedback, security=None):
        self.port = port
        self.connection_manager = connection_manager
        self.gamepad_emulator = gamepad_emulator
        self.haptic_feedback = haptic_feedback
        self.server_socket: Optional[bluetooth.BluetoothSocket] = None
        self._running = False
        self._client_sockets = {}  # client_id -> socket
        self.authenticator = StreamAuthenticator(security or SessionSecurity())
        self._pending_auth = set()
        self._sessions = {}
        
    async def start(self):
        """Start the Bluetooth server."""
        if bluetooth is None:
            logger.warning("pybluez not installed, Bluetooth RFCOMM server is disabled")
            return
        
        logger.info(f"Starting Bluetooth RFCOMM server on port {self.port}")
        
        try:
            # Create Bluetooth socket
            self.server_socket = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
            self.server_socket.bind(("", self.port))
            self.server_socket.listen(5)
            
            # Set socket to non-blocking
            self.server_socket.setblocking(False)
            
            self._running = True
            logger.info(f"Bluetooth server listening on port {self.port}")
            
            # Accept connections in a loop
            loop = asyncio.get_event_loop()
            while self._running:
                try:
                    client_socket, address = await loop.sock_accept(self.server_socket)
                    client_socket.setblocking(False)
                    
                    logger.info(f"Bluetooth connection from {address}")
                    asyncio.create_task(self._handle_client(client_socket, address))
                    
                except asyncio.CancelledError:
                    break
                except Exception as e:
                    if self._running:
                        logger.error(f"Error accepting Bluetooth connection: {e}")
                    await asyncio.sleep(0.1)
                    
        except Exception as e:
            logger.error(f"Failed to start Bluetooth server: {e}")
            raise
    
    async def stop(self):
        """Stop the Bluetooth server."""
        logger.info("Stopping Bluetooth server")
        self._running = False
        
        # Close all client connections
        for client_id, socket in self._client_sockets.items():
            try:
                socket.close()
            except Exception:
                pass
        
        self._client_sockets.clear()
        
        # Close server socket
        if self.server_socket:
            try:
                self.server_socket.close()
            except Exception as e:
                logger.error(f"Error closing server socket: {e}")
    
    async def _handle_client(self, client_socket, address):
        """Handle a Bluetooth client connection."""
        client_id = f"bt_{address[0]}"
        buffer = ""
        
        try:
            loop = asyncio.get_event_loop()
            
            while self._running:
                try:
                    data = await loop.sock_recv(client_socket, 1024)
                    if not data:
                        break
                    
                    buffer += data.decode('utf-8')
                    if len(buffer.encode('utf-8')) > MAX_MESSAGE_SIZE:
                        logger.warning("Closing Bluetooth connection with oversized message from %s", address)
                        break
                    
                    # Process complete messages
                    while '\n' in buffer:
                        line, buffer = buffer.split('\n', 1)
                        if line.strip():
                            await self._handle_message(client_id, line.strip(), client_socket, address)
                            
                except asyncio.CancelledError:
                    break
                except Exception as e:
                    logger.error(f"Error receiving from Bluetooth client: {e}")
                    await asyncio.sleep(0.1)
                    
        except Exception as e:
            logger.error(f"Bluetooth client error: {e}")
        finally:
            await self.connection_manager.disconnect_client(client_id)
            if client_id in self._client_sockets:
                del self._client_sockets[client_id]
            self._sessions.pop(client_socket, None)
            try:
                client_socket.close()
            except Exception:
                pass
            logger.info(f"Bluetooth client {client_id} disconnected")
    
    async def _handle_message(self, client_id: str, message: str, client_socket=None, address=None):
        """Handle a message from Bluetooth client."""
        try:
            data = json.loads(message)
            msg_type = data.get('type')
            session = self._sessions.get(client_socket)
            if session is not None:
                client_id = session.identity.device_id
            
            if msg_type == 'connect':
                response = self.authenticator.begin(data, address)
                self._pending_auth.add(client_socket)
                client_socket.send((json.dumps(response) + '\n').encode('utf-8'))
            elif msg_type == 'reconnect_begin':
                response = self.authenticator.begin_reconnect(data, address)
                self._pending_auth.add(client_socket)
                client_socket.send((json.dumps(response) + '\n').encode('utf-8'))
            elif msg_type == 'pair_proof':
                if client_socket not in self._pending_auth:
                    raise SessionSecurityError("authentication handshake not started")
                session = self.authenticator.complete(data, address)
                client_id = session.identity.device_id
                accepted = await self.connection_manager.connect_client(
                    client_id, 'bluetooth', str(address[0])
                )
                if not accepted:
                    raise SessionSecurityError("maximum clients reached")
                self._client_sockets[client_id] = client_socket
                self._sessions[client_socket] = session
                self._pending_auth.discard(client_socket)
                client_socket.send((json.dumps({
                    'type': 'connected',
                    'client_id': client_id,
                    'device_id': client_id,
                    'session_id': session.identity.session_id,
                    'server_challenge': data.get('challenge'),
                }) + '\n').encode('utf-8'))
            elif msg_type == 'reconnect_proof':
                if client_socket not in self._pending_auth:
                    raise SessionSecurityError("authentication handshake not started")
                session = self.authenticator.complete_reconnect(data, address)
                client_id = session.identity.device_id
                accepted = await self.connection_manager.connect_client(
                    client_id, 'bluetooth', str(address[0])
                )
                if not accepted:
                    raise SessionSecurityError("maximum clients reached")
                self._client_sockets[client_id] = client_socket
                self._sessions[client_socket] = session
                self._pending_auth.discard(client_socket)
                client_socket.send((json.dumps({
                    'type': 'connected',
                    'client_id': client_id,
                    'device_id': client_id,
                    'session_id': session.identity.session_id,
                    'server_challenge': data.get('challenge'),
                }) + '\n').encode('utf-8'))
            elif msg_type == 'input' and client_id in self._client_sockets:
                if session is None:
                    raise SessionSecurityError("authentication required")
                await self._handle_input(
                    client_id, self.authenticator.verify_message(session, data)
                )
            elif msg_type == 'heartbeat' and client_id in self._client_sockets:
                if session is None:
                    raise SessionSecurityError("authentication required")
                self.authenticator.verify_message(session, data)
                await self.connection_manager.update_activity(client_id)
            elif msg_type == 'ping' and client_id in self._client_sockets:
                if session is None:
                    raise SessionSecurityError("authentication required")
                self.authenticator.verify_message(session, data)
                await self._send_pong(client_id)
            elif msg_type in ('input', 'heartbeat', 'ping'):
                raise SessionSecurityError("authentication required")
                
        except (SessionSecurityError, ValueError, TypeError) as e:
            logger.warning("Rejected Bluetooth authentication: %s", e)
            if client_socket:
                client_socket.close()
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
    
    async def _send_pong(self, client_id: str):
        """Send pong response to ping."""
        if client_id not in self._client_sockets:
            return
        
        socket = self._client_sockets[client_id]
        
        try:
            response = json.dumps({
                'type': 'pong',
                'client_id': client_id,
                'timestamp': asyncio.get_event_loop().time()
            })
            socket.send((response + '\n').encode('utf-8'))
        except Exception as e:
            logger.error(f"Error sending pong: {e}")
    
    async def send_haptic_feedback(self, client_id: str, intensity: float, duration: float):
        """Send haptic feedback to client."""
        if client_id not in self._client_sockets:
            return
        
        socket = self._client_sockets[client_id]
        
        try:
            message = json.dumps({
                'type': 'haptic',
                'intensity': intensity,
                'duration': duration
            })
            socket.send((message + '\n').encode('utf-8'))
        except Exception as e:
            logger.error(f"Error sending haptic feedback: {e}")
