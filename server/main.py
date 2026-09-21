#!/usr/bin/env python3
"""
Virtual Gamepad Server - Main Entry Point
Mode "The Great": Advanced PC server for multi-connection gamepad emulation
"""

import asyncio
import json
import logging
from pathlib import Path
from core.connection_manager import ConnectionManager
from core.gamepad_emulator import GamepadEmulator
from core.keyboard_mouse import KeyboardMouseHandler
from core.haptic_feedback import HapticFeedbackManager
from core.adb_bridge import ADBBridge
from protocols.udp_server import UDPServer
from protocols.websocket_server import WebSocketServer
from protocols.bluetooth_server import BluetoothServer
from protocols.usb_server import USBServer

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class VirtualGamepadServer:
    def __init__(self, config_path: str = "config/server_config.json"):
        self.config = self._load_config(config_path)
        self.connection_manager = ConnectionManager(self.config)
        self.gamepad_emulator = GamepadEmulator(self.config)
        self.keyboard_mouse = KeyboardMouseHandler(self.config)
        self.haptic_feedback = HapticFeedbackManager(self.config)
        self.adb_bridge = ADBBridge(self.config)
        
        self.servers = {}
        self.running = False
        
    def _load_config(self, config_path: str) -> dict:
        """Load server configuration from JSON file."""
        try:
            with open(config_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            logger.warning(f"Config file not found at {config_path}, using defaults")
            return self._default_config()
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON in config file: {e}")
            return self._default_config()
    
    def _default_config(self) -> dict:
        """Return default configuration."""
        return {
            "server": {
                "host": "0.0.0.0",
                "udp_port": 8888,
                "websocket_port": 8889,
                "bluetooth_port": 8887,
                "usb_port": 8890
            },
            "gamepad": {
                "emulation_type": "xbox360",
                "vibration_enabled": True,
                "deadzone_left": 0.1,
                "deadzone_right": 0.1
            },
            "keyboard_mouse": {
                "enabled": True,
                "sensitivity": 1.0
            },
            "connection": {
                "max_clients": 4,
                "reconnect_interval": 5,
                "timeout": 30
            }
        }
    
    async def start(self):
        """Start all server protocols."""
        logger.info("Starting Virtual Gamepad Server...")
        
        # Initialize gamepad emulator
        await self.gamepad_emulator.initialize()
        
        # Initialize connection manager and haptics
        await self.connection_manager.start()
        await self.haptic_feedback.start()
        
        # Wire up disconnect callback to release/reset virtual gamepad
        self.connection_manager.register_disconnect_callback(self.gamepad_emulator.release_client)
        
        # Wire up native XInput rumble callback to dispatch to connected clients
        def _on_gamepad_vibration(slot, left, right):
            for cid, s in self.gamepad_emulator.client_slots.items():
                if s == slot:
                    asyncio.create_task(self.haptic_feedback.trigger_vibration(cid, left, right, duration=0.2))
                    break
            else:
                for cid in self.connection_manager.clients.keys():
                    asyncio.create_task(self.haptic_feedback.trigger_vibration(cid, left, right, duration=0.2))

        self.gamepad_emulator.register_vibration_listener(_on_gamepad_vibration)
        
        # Initialize keyboard/mouse handler if enabled
        if self.config.get('keyboard_mouse', {}).get('enabled', False):
            await self.keyboard_mouse.initialize()
        
        # Start protocol servers
        self.servers['udp'] = UDPServer(
            self.config['server']['host'],
            self.config['server']['udp_port'],
            self.connection_manager,
            self.gamepad_emulator,
            self.haptic_feedback
        )
        
        self.servers['websocket'] = WebSocketServer(
            self.config['server']['host'],
            self.config['server']['websocket_port'],
            self.connection_manager,
            self.gamepad_emulator,
            self.haptic_feedback
        )
        
        self.servers['bluetooth'] = BluetoothServer(
            self.config['server']['bluetooth_port'],
            self.connection_manager,
            self.gamepad_emulator,
            self.haptic_feedback
        )
        
        self.servers['usb'] = USBServer(
            self.config['server']['usb_port'],
            self.connection_manager,
            self.gamepad_emulator,
            self.haptic_feedback,
            self.adb_bridge
        )
        
        # Start all servers
        tasks = []
        for name, server in self.servers.items():
            logger.info(f"Starting {name.upper()} server...")
            tasks.append(server.start())
        
        self.running = True
        await asyncio.gather(*tasks)
    
    async def stop(self):
        """Stop all server protocols."""
        logger.info("Stopping Virtual Gamepad Server...")
        self.running = False
        
        for name, server in self.servers.items():
            await server.stop()
        
        await self.connection_manager.stop()
        await self.haptic_feedback.stop()
        await self.keyboard_mouse.cleanup()
        await self.gamepad_emulator.cleanup()
        logger.info("Server stopped")


async def main():
    """Main entry point."""
    server = VirtualGamepadServer()
    
    try:
        await server.start()
    except KeyboardInterrupt:
        logger.info("Received interrupt signal")
    except Exception as e:
        logger.error(f"Server error: {e}", exc_info=True)
    finally:
        await server.stop()


if __name__ == "__main__":
    asyncio.run(main())
