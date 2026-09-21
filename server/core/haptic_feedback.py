"""
Haptic Feedback Manager - Handles vibration feedback to clients
"""

import asyncio
import logging
from typing import Dict, Optional, Callable
from dataclasses import dataclass
from datetime import datetime

logger = logging.getLogger(__name__)


@dataclass
class VibrationCommand:
    """Represents a vibration command."""
    client_id: str
    left_motor: float  # 0.0 to 1.0
    right_motor: float  # 0.0 to 1.0
    duration: float  # seconds
    timestamp: datetime


class HapticFeedbackManager:
    """Manages haptic feedback for connected clients."""
    
    def __init__(self, config: dict):
        self.config = config
        self.enabled = config.get('gamepad', {}).get('vibration_enabled', True)
        self.active_vibrations: Dict[str, VibrationCommand] = {}
        self._vibration_senders: list = []
        self._cleanup_task: Optional[asyncio.Task] = None

    def register_sender(self, sender: Callable):
        """Register a protocol sender: sender(client_id, left, right, duration)."""
        self._vibration_senders.append(sender)
        
    async def start(self):
        """Start the haptic feedback manager."""
        logger.info("Starting Haptic Feedback Manager")
        self._cleanup_task = asyncio.create_task(self._cleanup_expired_vibrations())
        
    async def stop(self):
        """Stop the haptic feedback manager."""
        logger.info("Stopping Haptic Feedback Manager")
        if self._cleanup_task:
            self._cleanup_task.cancel()
            try:
                await self._cleanup_task
            except asyncio.CancelledError:
                pass
        
        self.active_vibrations.clear()
    
    async def trigger_vibration(self, client_id: str, left_motor: float, right_motor: float, duration: float = 0.2):
        """Trigger vibration for a specific client and dispatch to protocol senders."""
        if not self.enabled:
            return
        
        # Clamp values
        left_motor = max(0.0, min(1.0, left_motor))
        right_motor = max(0.0, min(1.0, right_motor))
        duration = max(0.0, min(10.0, duration))
        
        if left_motor == 0.0 and right_motor == 0.0:
            return
        
        command = VibrationCommand(
            client_id=client_id,
            left_motor=left_motor,
            right_motor=right_motor,
            duration=duration,
            timestamp=datetime.now()
        )
        
        self.active_vibrations[client_id] = command
        logger.debug(f"Vibration triggered for {client_id}: L={left_motor}, R={right_motor}, dur={duration}s")
        
        for sender in self._vibration_senders:
            try:
                res = sender(client_id, left_motor, right_motor, duration)
                if asyncio.iscoroutine(res):
                    await res
            except Exception as e:
                logger.error(f"Error in vibration sender callback: {e}")
    
    async def cancel_vibration(self, client_id: str):
        """Cancel active vibration for a client."""
        if client_id in self.active_vibrations:
            del self.active_vibrations[client_id]
            logger.debug(f"Vibration cancelled for {client_id}")
    
    def get_active_vibration(self, client_id: str) -> Optional[VibrationCommand]:
        """Get active vibration command for a client."""
        return self.active_vibrations.get(client_id)
    
    async def _cleanup_expired_vibrations(self):
        """Periodically clean up expired vibration commands."""
        while True:
            try:
                await asyncio.sleep(0.1)  # Check every 100ms
                
                now = datetime.now()
                expired_clients = []
                
                for client_id, command in self.active_vibrations.items():
                    elapsed = (now - command.timestamp).total_seconds()
                    if elapsed >= command.duration:
                        expired_clients.append(client_id)
                
                for client_id in expired_clients:
                    await self.cancel_vibration(client_id)
                    
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in vibration cleanup task: {e}")
    
    async def handle_game_event(self, event_type: str, intensity: float = 0.5):
        """Handle game events and trigger appropriate haptic feedback."""
        if not self.enabled:
            return
        
        # Map game event types to vibration patterns
        vibration_patterns = {
            'collision': {'left': 0.8, 'right': 0.8, 'duration': 0.2},
            'explosion': {'left': 1.0, 'right': 1.0, 'duration': 0.5},
            'shot': {'left': 0.3, 'right': 0.3, 'duration': 0.1},
            'impact': {'left': 0.6, 'right': 0.4, 'duration': 0.15},
            'alert': {'left': 0.5, 'right': 0.5, 'duration': 0.3},
        }
        
        if event_type in vibration_patterns:
            pattern = vibration_patterns[event_type]
            # This would be sent to all connected clients
            # The actual sending is handled by the protocol servers
            logger.debug(f"Game event: {event_type} -> vibration pattern applied")
