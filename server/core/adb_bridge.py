"""
ADB Bridge - Handles ADB USB connection automation
"""

import asyncio
import logging
import subprocess
from typing import Optional, List

logger = logging.getLogger(__name__)


class ADBBridge:
    """Manages ADB USB bridge for zero-latency connection."""
    
    def __init__(self, config: dict):
        self.config = config
        self.usb_port = config.get('server', {}).get('usb_port', 8890)
        self._adb_available = False
        self._connected_devices: List[str] = []
        
    async def initialize(self):
        """Initialize ADB bridge and check if ADB is available."""
        try:
            # Check if ADB is available
            result = await self._run_adb_command(['version'])
            if result and 'Android Debug Bridge' in result:
                self._adb_available = True
                logger.info("ADB is available")
                await self._list_devices()
            else:
                logger.warning("ADB not found or not working")
                self._adb_available = False
        except Exception as e:
            logger.error(f"Error initializing ADB bridge: {e}")
            self._adb_available = False
    
    async def _run_adb_command(
        self, args: List[str], *, log_errors: bool = True
    ) -> Optional[str]:
        """Run an ADB command and return the output."""
        try:
            command = ['adb'] + args
            process = await asyncio.create_subprocess_exec(
                *command,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE
            )
            stdout, stderr = await process.communicate()
            
            if process.returncode == 0:
                return stdout.decode('utf-8').strip()
            else:
                if log_errors:
                    logger.error(f"ADB command failed: {stderr.decode('utf-8').strip()}")
                return None
        except FileNotFoundError:
            logger.error("ADB executable not found")
            return None
        except Exception as e:
            logger.error(f"Error running ADB command: {e}")
            return None
    
    async def _list_devices(self):
        """List connected ADB devices."""
        result = await self._run_adb_command(['devices'])
        if result:
            lines = result.split('\n')
            self._connected_devices = []
            for line in lines[1:]:  # Skip header
                if line.strip():
                    device_id = line.split('\t')[0]
                    self._connected_devices.append(device_id)
                    logger.info(f"Found ADB device: {device_id}")
    
    async def setup_port_forwarding(self, device_id: Optional[str] = None) -> bool:
        """Set up ADB port forwarding for USB connection."""
        if not self._adb_available:
            logger.error("ADB not available, cannot set up port forwarding")
            return False
        
        if not self._connected_devices:
            logger.warning("No ADB devices connected")
            await self._list_devices()
            if not self._connected_devices:
                return False
        
        # Use first device if none specified
        target_device = device_id or self._connected_devices[0]
        
        try:
            # Remove any existing reverse forwarding
            # Removing a missing reverse is expected on the first setup and
            # must not be reported as a failed bridge operation.
            await self._run_adb_command(
                ['-s', target_device, 'reverse', '--remove', f'tcp:{self.usb_port}'],
                log_errors=False,
            )
            
            # Set up new reverse forwarding (phone localhost:port -> PC localhost:port)
            result = await self._run_adb_command([
                '-s', target_device,
                'reverse',
                f'tcp:{self.usb_port}',
                f'tcp:{self.usb_port}'
            ])
            
            if result is not None:
                logger.info(f"ADB reverse port forwarding set up for device {target_device}: {self.usb_port}")
                return True
            else:
                logger.error("Failed to set up ADB reverse port forwarding")
                return False
                
        except Exception as e:
            logger.error(f"Error setting up reverse port forwarding: {e}")
            return False
    
    async def remove_port_forwarding(self, device_id: Optional[str] = None):
        """Remove ADB reverse port forwarding."""
        if not self._adb_available:
            return
        
        if not self._connected_devices:
            return
        
        target_device = device_id or self._connected_devices[0]
        
        try:
            await self._run_adb_command(['-s', target_device, 'reverse', '--remove', f'tcp:{self.usb_port}'])
            logger.info(f"ADB reverse port forwarding removed for device {target_device}")
        except Exception as e:
            logger.error(f"Error removing reverse port forwarding: {e}")
    
    async def get_device_info(self, device_id: Optional[str] = None) -> Optional[dict]:
        """Get information about a connected device."""
        if not self._adb_available:
            return None
        
        target_device = device_id or (self._connected_devices[0] if self._connected_devices else None)
        if not target_device:
            return None
        
        try:
            # Get device properties
            result = await self._run_adb_command(['-s', target_device, 'shell', 'getprop'])
            if result:
                props = {}
                for line in result.split('\n'):
                    if ':' in line:
                        key, value = line.split(':', 1)
                        props[key.strip()] = value.strip().strip('[]')
                return props
        except Exception as e:
            logger.error(f"Error getting device info: {e}")
        
        return None
    
    def is_available(self) -> bool:
        """Check if ADB is available."""
        return self._adb_available
    
    def get_connected_devices(self) -> List[str]:
        """Get list of connected device IDs."""
        return self._connected_devices.copy()
    
    def get_setup_instructions(self) -> str:
        """Get manual setup instructions for ADB."""
        return """
ADB USB Bridge Setup Instructions:

1. Install ADB (Android Debug Bridge):
   - Download Android Platform Tools from: https://developer.android.com/studio/releases/platform-tools
   - Extract and add to your system PATH

2. Enable USB Debugging on your Android device:
   - Go to Settings > About Phone
   - Tap "Build Number" 7 times to enable Developer Options
   - Go to Settings > Developer Options
   - Enable "USB Debugging"

3. Connect your device via USB:
   - Connect your Android device to your PC via USB
   - Accept the debugging prompt on your phone

4. Verify ADB connection:
   - Run: adb devices
   - You should see your device listed

5. Set up port forwarding (if not automatic):
   - Run: adb reverse tcp:8890 tcp:8890

6. Start the server and connect via USB mode in the app
"""
