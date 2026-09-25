"""
Backend Capabilities - Defines supported features for each backend
"""

from dataclasses import dataclass
from typing import Dict
from enum import Enum


class EmulationType(Enum):
    """Supported gamepad emulation types."""
    XBOX_360 = "xbox360"
    DUALSHOCK_4 = "dualshock4"


class SupportLevel(Enum):
    """How much of a capability is implemented by the local backend."""
    SUPPORTED = "supported"
    PARTIAL = "partial"
    UNSUPPORTED = "unsupported"


@dataclass
class ButtonCapability:
    """Represents a button's availability and mapping."""
    name: str
    supported: bool
    backend_name: str = ""  # vgamepad constant or HID bit position
    notes: str = ""
    level: SupportLevel = SupportLevel.SUPPORTED

    def __post_init__(self):
        if not self.supported:
            self.level = SupportLevel.UNSUPPORTED


@dataclass
class AxisCapability:
    """Represents an analog axis's availability and range."""
    name: str
    supported: bool
    min_value: float
    max_value: float
    backend_name: str = ""
    notes: str = ""
    level: SupportLevel = SupportLevel.SUPPORTED

    def __post_init__(self):
        if not self.supported:
            self.level = SupportLevel.UNSUPPORTED


@dataclass
class BackendCapabilities:
    """Complete capability set for a backend."""
    backend_name: str
    emulation_type: EmulationType
    
    # Digital buttons
    buttons: Dict[str, ButtonCapability]
    
    # Analog axes
    axes: Dict[str, AxisCapability]
    
    # Special features
    vibration: bool
    vibration_feedback: bool  # Native game → client vibration
    led_support: bool
    gyro_input: bool  # Client → server gyroscope data
    
    # Connection features
    multi_client: bool
    max_clients: int
    
    # Report format
    report_size_bytes: int
    
    # Notes
    notes: str = ""
    feature_levels: Dict[str, SupportLevel] = None

    def feature_status(self, feature: str) -> SupportLevel:
        """Return the verified status of a non-button/axis feature."""
        if self.feature_levels and feature in self.feature_levels:
            return self.feature_levels[feature]
        value = getattr(self, feature)
        return SupportLevel.SUPPORTED if value else SupportLevel.UNSUPPORTED


def get_xbox360_capabilities() -> BackendCapabilities:
    """Xbox 360 emulation capabilities via vgamepad."""
    return BackendCapabilities(
        backend_name="Xbox 360 (vgamepad)",
        emulation_type=EmulationType.XBOX_360,
        
        buttons={
            "a": ButtonCapability("a", True, "XUSB_GAMEPAD_A"),
            "b": ButtonCapability("b", True, "XUSB_GAMEPAD_B"),
            "x": ButtonCapability("x", True, "XUSB_GAMEPAD_X"),
            "y": ButtonCapability("y", True, "XUSB_GAMEPAD_Y"),
            "left_bumper": ButtonCapability("left_bumper", True, "XUSB_GAMEPAD_LEFT_SHOULDER"),
            "right_bumper": ButtonCapability("right_bumper", True, "XUSB_GAMEPAD_RIGHT_SHOULDER"),
            "back": ButtonCapability("back", True, "XUSB_GAMEPAD_BACK"),
            "start": ButtonCapability("start", True, "XUSB_GAMEPAD_START"),
            "left_thumb": ButtonCapability("left_thumb", True, "XUSB_GAMEPAD_LEFT_THUMB"),
            "right_thumb": ButtonCapability("right_thumb", True, "XUSB_GAMEPAD_RIGHT_THUMB"),
            "dpad_up": ButtonCapability("dpad_up", True, "XUSB_GAMEPAD_DPAD_UP"),
            "dpad_down": ButtonCapability("dpad_down", True, "XUSB_GAMEPAD_DPAD_DOWN"),
            "dpad_left": ButtonCapability("dpad_left", True, "XUSB_GAMEPAD_DPAD_LEFT"),
            "dpad_right": ButtonCapability("dpad_right", True, "XUSB_GAMEPAD_DPAD_RIGHT"),
            "guide": ButtonCapability("guide", False, "XUSB_GAMEPAD_GUIDE",
                                    "The emulator never updates this button"),
        },
        
        axes={
            "left_trigger": AxisCapability("left_trigger", True, 0.0, 1.0, "0-255"),
            "right_trigger": AxisCapability("right_trigger", True, 0.0, 1.0, "0-255"),
            "left_stick_x": AxisCapability("left_stick_x", True, -1.0, 1.0, "-32767 to 32767"),
            "left_stick_y": AxisCapability("left_stick_y", True, -1.0, 1.0, "-32767 to 32767"),
            "right_stick_x": AxisCapability("right_stick_x", True, -1.0, 1.0, "-32767 to 32767"),
            "right_stick_y": AxisCapability("right_stick_y", True, -1.0, 1.0, "-32767 to 32767"),
        },
        
        vibration=False,  # No client output transport is attached to this backend
        vibration_feedback=True,  # Native XInput rumble via register_notification
        led_support=False,  # No LED API is called by the emulator
        gyro_input=False,  # Gyroscope sent separately, not part of gamepad state
        
        multi_client=True,
        max_clients=4,  # XInput slots 1-4
        
        report_size_bytes=0,  # Internal vgamepad report
        
        notes="Requires ViGEm Bus driver on Windows. Linux support is experimental."
    )


def get_dualshock4_capabilities() -> BackendCapabilities:
    """DualShock 4 emulation capabilities via vgamepad."""
    buttons = {
            "a": ButtonCapability("a", True, "DS4_BUTTON_CROSS", "Mapping is not used by update_state"),
            "b": ButtonCapability("b", True, "DS4_BUTTON_CIRCLE", "Mapping is not used by update_state"),
            "x": ButtonCapability("x", True, "DS4_BUTTON_SQUARE", "Mapping is not used by update_state"),
            "y": ButtonCapability("y", True, "DS4_BUTTON_TRIANGLE", "Mapping is not used by update_state"),
            "left_bumper": ButtonCapability("left_bumper", True, "DS4_BUTTON_SHOULDER_LEFT"),
            "right_bumper": ButtonCapability("right_bumper", True, "DS4_BUTTON_SHOULDER_RIGHT"),
            "back": ButtonCapability("back", True, "DS4_BUTTON_SHARE"),
            "start": ButtonCapability("start", True, "DS4_BUTTON_OPTIONS"),
            "left_thumb": ButtonCapability("left_thumb", True, "DS4_BUTTON_THUMB_LEFT"),
            "right_thumb": ButtonCapability("right_thumb", True, "DS4_BUTTON_THUMB_RIGHT"),
            "dpad_up": ButtonCapability("dpad_up", True, "DS4_DPAD_DIRECTIONS"),
            "dpad_down": ButtonCapability("dpad_down", True, "DS4_DPAD_DIRECTIONS"),
            "dpad_left": ButtonCapability("dpad_left", True, "DS4_DPAD_DIRECTIONS"),
            "dpad_right": ButtonCapability("dpad_right", True, "DS4_DPAD_DIRECTIONS"),
        }
    axes = {
            "left_trigger": AxisCapability("left_trigger", True, 0.0, 1.0, "0-255"),
            "right_trigger": AxisCapability("right_trigger", True, 0.0, 1.0, "0-255"),
            "left_stick_x": AxisCapability("left_stick_x", True, -1.0, 1.0, "0-255 centered at 128"),
            "left_stick_y": AxisCapability("left_stick_y", True, -1.0, 1.0, "0-255 centered at 128"),
            "right_stick_x": AxisCapability("right_stick_x", True, -1.0, 1.0, "0-255 centered at 128"),
            "right_stick_y": AxisCapability("right_stick_y", True, -1.0, 1.0, "0-255 centered at 128"),
        }
    # update_state currently always uses XUSB_BUTTON and XUSB axis methods.
    for capability in (*buttons.values(), *axes.values()):
        capability.supported = False
        capability.level = SupportLevel.UNSUPPORTED

    return BackendCapabilities(
        backend_name="DualShock 4 (vgamepad)",
        emulation_type=EmulationType.DUALSHOCK_4,
        buttons=buttons,
        axes=axes,
        vibration=False,
        vibration_feedback=False,  # DS4 vibration not implemented in current code
        led_support=False,  # No DS4 light-bar API is called
        gyro_input=False,  # Gyroscope sent separately
        
        multi_client=True,
        max_clients=4,  # Can create multiple DS4 instances
        
        report_size_bytes=0,  # Internal vgamepad report
        
        notes="DS4 construction exists, but update_state sends XUSB constants; controls are not currently wired."
    )


def get_bluetooth_hid_capabilities() -> BackendCapabilities:
    """Bluetooth HID Plug & Play capabilities."""
    return BackendCapabilities(
        backend_name="Bluetooth HID (Android)",
        emulation_type=EmulationType.XBOX_360,  # HID descriptor matches Xbox layout
        
        buttons={
            "a": ButtonCapability("a", True, "Bit 0", "16-bit button field"),
            "b": ButtonCapability("b", True, "Bit 1"),
            "x": ButtonCapability("x", True, "Bit 2"),
            "y": ButtonCapability("y", True, "Bit 3"),
            "left_bumper": ButtonCapability("left_bumper", True, "Bit 4"),
            "right_bumper": ButtonCapability("right_bumper", True, "Bit 5"),
            "back": ButtonCapability("back", True, "Bit 6"),
            "start": ButtonCapability("start", True, "Bit 7"),
            "left_thumb": ButtonCapability("left_thumb", True, "Bit 8"),
            "right_thumb": ButtonCapability("right_thumb", True, "Bit 9"),
            "dpad_up": ButtonCapability("dpad_up", True, "Bit 10"),
            "dpad_down": ButtonCapability("dpad_down", True, "Bit 11"),
            "dpad_left": ButtonCapability("dpad_left", True, "Bit 12"),
            "dpad_right": ButtonCapability("dpad_right", True, "Bit 13"),
            # Bits 14-15 reserved for future use
        },
        
        axes={
            "left_trigger": AxisCapability("left_trigger", True, 0.0, 1.0, "0-255"),
            "right_trigger": AxisCapability("right_trigger", True, 0.0, 1.0, "0-255"),
            "left_stick_x": AxisCapability("left_stick_x", True, -1.0, 1.0, "-127 to 127"),
            "left_stick_y": AxisCapability("left_stick_y", True, -1.0, 1.0, "-127 to 127"),
            "right_stick_x": AxisCapability("right_stick_x", True, -1.0, 1.0, "-127 to 127"),
            "right_stick_y": AxisCapability("right_stick_y", True, -1.0, 1.0, "-127 to 127"),
        },
        
        vibration=False,  # HID output reports not implemented
        vibration_feedback=False,
        led_support=False,
        gyro_input=False,
        
        multi_client=False,  # Single device mode
        max_clients=1,
        
        report_size_bytes=8,  # Fixed 8-byte HID report
        
        notes="Android 9.0+ required. HID input reports are built and sent by the Android app; host compatibility is not guaranteed."
    )


def get_network_capabilities(protocol: str) -> BackendCapabilities:
    """Network protocol capabilities (UDP/WebSocket)."""
    return BackendCapabilities(
        backend_name=f"Network ({protocol})",
        emulation_type=EmulationType.XBOX_360,  # Uses Xbox layout by default
        
        buttons={
            "a": ButtonCapability("a", True, "JSON field 'a'"),
            "b": ButtonCapability("b", True, "JSON field 'b'"),
            "x": ButtonCapability("x", True, "JSON field 'x'"),
            "y": ButtonCapability("y", True, "JSON field 'y'"),
            "left_bumper": ButtonCapability("left_bumper", True, "JSON field 'left_bumper'"),
            "right_bumper": ButtonCapability("right_bumper", True, "JSON field 'right_bumper'"),
            "back": ButtonCapability("back", True, "JSON field 'back'"),
            "start": ButtonCapability("start", True, "JSON field 'start'"),
            "left_thumb": ButtonCapability("left_thumb", True, "JSON field 'left_thumb'"),
            "right_thumb": ButtonCapability("right_thumb", True, "JSON field 'right_thumb'"),
            "dpad_up": ButtonCapability("dpad_up", True, "JSON field 'dpad_up'"),
            "dpad_down": ButtonCapability("dpad_down", True, "JSON field 'dpad_down'"),
            "dpad_left": ButtonCapability("dpad_left", True, "JSON field 'dpad_left'"),
            "dpad_right": ButtonCapability("dpad_right", True, "JSON field 'dpad_right'"),
        },
        
        axes={
            "left_trigger": AxisCapability("left_trigger", True, 0.0, 1.0, "JSON float"),
            "right_trigger": AxisCapability("right_trigger", True, 0.0, 1.0, "JSON float"),
            "left_stick_x": AxisCapability("left_stick_x", True, -1.0, 1.0, "JSON float"),
            "left_stick_y": AxisCapability("left_stick_y", True, -1.0, 1.0, "JSON float"),
            "right_stick_x": AxisCapability("right_stick_x", True, -1.0, 1.0, "JSON float"),
            "right_stick_y": AxisCapability("right_stick_y", True, -1.0, 1.0, "JSON float"),
        },
        
        vibration=False,  # Transport emission exists, client consumption is incomplete
        vibration_feedback=False,  # Forwarding exists, end-to-end handling is incomplete
        led_support=False,
        gyro_input=False,  # Fields are ignored by GamepadState
        
        multi_client=True,
        max_clients=4,  # Limited by connection manager config
        
        report_size_bytes=0,  # JSON serialization, variable size
        
        notes=f"Uses JSON serialization. {protocol.upper()} protocol. Gyroscope fields are ignored by GamepadState.",
        feature_levels={
            "vibration": SupportLevel.PARTIAL,
            "vibration_feedback": SupportLevel.PARTIAL,
            "gyro_input": SupportLevel.UNSUPPORTED,
        }
    )


def get_usb_adb_capabilities() -> BackendCapabilities:
    """USB/ADB bridge capabilities."""
    return BackendCapabilities(
        backend_name="USB/ADB",
        emulation_type=EmulationType.XBOX_360,
        
        buttons={
            "a": ButtonCapability("a", True, "JSON field 'a'"),
            "b": ButtonCapability("b", True, "JSON field 'b'"),
            "x": ButtonCapability("x", True, "JSON field 'x'"),
            "y": ButtonCapability("y", True, "JSON field 'y'"),
            "left_bumper": ButtonCapability("left_bumper", True, "JSON field 'left_bumper'"),
            "right_bumper": ButtonCapability("right_bumper", True, "JSON field 'right_bumper'"),
            "back": ButtonCapability("back", True, "JSON field 'back'"),
            "start": ButtonCapability("start", True, "JSON field 'start'"),
            "left_thumb": ButtonCapability("left_thumb", True, "JSON field 'left_thumb'"),
            "right_thumb": ButtonCapability("right_thumb", True, "JSON field 'right_thumb'"),
            "dpad_up": ButtonCapability("dpad_up", True, "JSON field 'dpad_up'"),
            "dpad_down": ButtonCapability("dpad_down", True, "JSON field 'dpad_down'"),
            "dpad_left": ButtonCapability("dpad_left", True, "JSON field 'dpad_left'"),
            "dpad_right": ButtonCapability("dpad_right", True, "JSON field 'dpad_right'"),
        },
        
        axes={
            "left_trigger": AxisCapability("left_trigger", True, 0.0, 1.0, "JSON float"),
            "right_trigger": AxisCapability("right_trigger", True, 0.0, 1.0, "JSON float"),
            "left_stick_x": AxisCapability("left_stick_x", True, -1.0, 1.0, "JSON float"),
            "left_stick_y": AxisCapability("left_stick_y", True, -1.0, 1.0, "JSON float"),
            "right_stick_x": AxisCapability("right_stick_x", True, -1.0, 1.0, "JSON float"),
            "right_stick_y": AxisCapability("right_stick_y", True, -1.0, 1.0, "JSON float"),
        },
        
        vibration=False,  # Transport emission exists, client consumption is incomplete
        vibration_feedback=False,  # Forwarding exists, end-to-end handling is incomplete
        led_support=False,
        gyro_input=False,  # Fields are ignored by GamepadState
        
        multi_client=True,
        max_clients=4,
        
        report_size_bytes=0,  # JSON serialization
        
        notes="Uses JSON over ADB port forwarding. The Android client does not read server haptic messages.",
        feature_levels={
            "vibration": SupportLevel.PARTIAL,
            "vibration_feedback": SupportLevel.PARTIAL,
            "gyro_input": SupportLevel.UNSUPPORTED,
        }
    )


def get_bluetooth_rfcomm_capabilities() -> BackendCapabilities:
    """Bluetooth RFCOMM transport capabilities."""
    capabilities = get_network_capabilities("Bluetooth RFCOMM")
    capabilities.backend_name = "Bluetooth RFCOMM"
    capabilities.multi_client = False
    capabilities.max_clients = 1
    capabilities.vibration = False
    capabilities.vibration_feedback = False
    capabilities.feature_levels = {
        "vibration": SupportLevel.UNSUPPORTED,
        "vibration_feedback": SupportLevel.UNSUPPORTED,
        "gyro_input": SupportLevel.UNSUPPORTED,
        "multi_client": SupportLevel.UNSUPPORTED,
    }
    capabilities.notes = "Server accepts newline-delimited JSON, but Android connect() is a device-selection placeholder and server state is shared."
    return capabilities


def get_all_capabilities() -> Dict[str, BackendCapabilities]:
    """Get capabilities for all backends."""
    return {
        "xbox360": get_xbox360_capabilities(),
        "dualshock4": get_dualshock4_capabilities(),
        "bluetooth_hid": get_bluetooth_hid_capabilities(),
        "udp": get_network_capabilities("UDP"),
        "websocket": get_network_capabilities("WebSocket"),
        "usb_adb": get_usb_adb_capabilities(),
        "bluetooth_rfcomm": get_bluetooth_rfcomm_capabilities(),
    }


def get_capability_matrix() -> Dict[str, Dict[str, str]]:
    """Generate a matrix using supported, partial, and unsupported statuses."""
    all_caps = get_all_capabilities()
    
    # Extract all unique button and axis names
    all_buttons = set()
    all_axes = set()
    for caps in all_caps.values():
        all_buttons.update(caps.buttons.keys())
        all_axes.update(caps.axes.keys())
    
    matrix = {}
    for backend_name, caps in all_caps.items():
        matrix[backend_name] = {
            **{f"btn_{btn}": caps.buttons.get(btn, ButtonCapability(btn, False)).level.value
               for btn in sorted(all_buttons)},
            **{f"axis_{axis}": caps.axes.get(axis, AxisCapability(axis, False, 0, 0)).level.value
               for axis in sorted(all_axes)},
            "vibration": caps.feature_status("vibration").value,
            "vibration_feedback": caps.feature_status("vibration_feedback").value,
            "led_support": caps.feature_status("led_support").value,
            "gyro_input": caps.feature_status("gyro_input").value,
            "multi_client": caps.feature_status("multi_client").value,
        }
    
    return matrix
