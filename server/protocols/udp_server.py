"""
UDP Server - High-performance UDP protocol for local Wi-Fi connection
"""

import asyncio
import logging
import json
from typing import Optional

logger = logging.getLogger(__name__)


class UDPServer:
    """UDP server for low-latency gamepad input."""
    
    def __init__(self, host: str, port: int, connection_manager, gamepad_emulator, haptic_feedback):
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
            client_id = message.get('client_id')
            
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

            if not client_id:
                logger.warning("Received message without client_id")
                return
            
            # Register client if new
            accepted = await self.connection_manager.connect_client(
                client_id,
                'udp',
                f"{addr[0]}:{addr[1]}"
            )
            if not accepted:
                return
            
            self.client_addrs[client_id] = addr
            
            if msg_type == 'input':
                await self._handle_input(client_id, message.get('data', {}))
            elif msg_type == 'heartbeat':
                await self.connection_manager.update_activity(client_id)
            elif msg_type == 'ping':
                await self._send_pong(client_id, addr)
                
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON received: {e}")
        except Exception as e:
            logger.error(f"Error handling datagram: {e}")
    
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
