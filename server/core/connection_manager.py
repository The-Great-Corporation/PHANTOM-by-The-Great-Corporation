"""
Connection Manager - Handles multi-connection management for all protocols
"""

import asyncio
import logging
from typing import Dict, Optional, Callable
from datetime import datetime
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class ClientConnection:
    """Represents a connected client."""
    client_id: str
    protocol: str  # 'udp', 'websocket', 'bluetooth', 'usb'
    address: str
    connected_at: datetime
    last_activity: datetime
    latency_ms: float = 0.0


class ConnectionManager:
    """Manages multiple client connections across different protocols."""
    
    def __init__(self, config: dict):
        self.config = config
        self.clients: Dict[str, ClientConnection] = {}
        self.max_clients = config.get('connection', {}).get('max_clients', 4)
        self.timeout = config.get('connection', {}).get('timeout', 30)
        self.reconnect_interval = config.get('connection', {}).get('reconnect_interval', 5)
        self._cleanup_task: Optional[asyncio.Task] = None
        self._input_callbacks: list = []
        self._disconnect_callbacks: list = []
        
    async def start(self):
        """Start the connection manager."""
        logger.info("Starting Connection Manager")
        self._cleanup_task = asyncio.create_task(self._cleanup_inactive_clients())
        
    async def stop(self):
        """Stop the connection manager."""
        logger.info("Stopping Connection Manager")
        if self._cleanup_task:
            self._cleanup_task.cancel()
            try:
                await self._cleanup_task
            except asyncio.CancelledError:
                pass
        
        # Disconnect all clients
        for client_id in list(self.clients.keys()):
            await self.disconnect_client(client_id)
    
    def register_input_callback(self, callback: Callable):
        """Register a callback for handling input data."""
        self._input_callbacks.append(callback)

    def register_disconnect_callback(self, callback: Callable):
        """Register a callback called when a client disconnects."""
        self._disconnect_callbacks.append(callback)
    
    async def connect_client(self, client_id: str, protocol: str, address: str) -> bool:
        """Register a new client connection."""
        if client_id in self.clients:
            logger.info(f"Client {client_id} already connected, refreshing activity")
            client = self.clients[client_id]
            client.last_activity = datetime.now()
            return True

        if len(self.clients) >= self.max_clients:
            logger.warning(f"Max clients ({self.max_clients}) reached, rejecting {client_id}")
            return False
        
        client = ClientConnection(
            client_id=client_id,
            protocol=protocol,
            address=address,
            connected_at=datetime.now(),
            last_activity=datetime.now()
        )
        
        self.clients[client_id] = client
        logger.info(f"Client {client_id} connected via {protocol} from {address}")
        return True
    
    async def disconnect_client(self, client_id: str):
        """Disconnect a client."""
        if client_id in self.clients:
            client = self.clients[client_id]
            logger.info(f"Client {client_id} disconnected (was connected via {client.protocol})")
            del self.clients[client_id]
            
            for callback in self._disconnect_callbacks:
                try:
                    res = callback(client_id)
                    if asyncio.iscoroutine(res):
                        await res
                except Exception as e:
                    logger.error(f"Error in disconnect callback: {e}")
    
    async def update_activity(self, client_id: str, latency_ms: float = 0.0):
        """Update client activity timestamp and latency."""
        if client_id in self.clients:
            self.clients[client_id].last_activity = datetime.now()
            self.clients[client_id].latency_ms = latency_ms
    
    async def handle_input(self, client_id: str, input_data: dict):
        """Handle input data from a client."""
        await self.update_activity(client_id)
        
        # Call registered input callbacks
        for callback in self._input_callbacks:
            try:
                await callback(client_id, input_data)
            except Exception as e:
                logger.error(f"Error in input callback: {e}")
    
    def get_client(self, client_id: str) -> Optional[ClientConnection]:
        """Get client information."""
        return self.clients.get(client_id)
    
    def get_all_clients(self) -> Dict[str, ClientConnection]:
        """Get all connected clients."""
        return self.clients.copy()
    
    def get_client_count(self) -> int:
        """Get the number of connected clients."""
        return len(self.clients)
    
    async def _cleanup_inactive_clients(self):
        """Periodically clean up inactive clients."""
        while True:
            try:
                await asyncio.sleep(5)  # Check every 5 seconds
                
                now = datetime.now()
                inactive_clients = []
                
                for client_id, client in self.clients.items():
                    inactive_seconds = (now - client.last_activity).total_seconds()
                    if inactive_seconds > self.timeout:
                        inactive_clients.append(client_id)
                        logger.warning(
                            "Client %s inactive for %.1fs (protocol=%s, address=%s, timeout=%ss)",
                            client_id,
                            inactive_seconds,
                            client.protocol,
                            client.address,
                            self.timeout,
                        )
                
                for client_id in inactive_clients:
                    await self.disconnect_client(client_id)
                    
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in cleanup task: {e}")
    
    async def send_haptic_feedback(self, client_id: str, intensity: float, duration: float):
        """Send haptic feedback to a specific client."""
        # This will be implemented by the protocol servers
        logger.debug(f"Haptic feedback for {client_id}: intensity={intensity}, duration={duration}")
