# GamepadState Specification

## Overview

This document formalizes the `GamepadState` data structure used across the PHANTOM system, defining all supported controls, their valid types, ranges, and backend compatibility.

## Purpose

The `GamepadState` represents the complete input state of a virtual gamepad controller. It is used to:
- Transmit input data from Android client to PC server
- Store the current state in the gamepad emulator
- Generate HID reports for Bluetooth Plug & Play mode
- Serve as the canonical source of truth for controller state

## Control Categories

### 1. Digital Buttons (Boolean)

All digital buttons are boolean values (`True`/`False`) representing pressed/released state.

| Field | Description | Xbox 360 Button | vgamepad Constant |
|-------|-------------|-----------------|-------------------|
| `a` | Green action button | A | `XUSB_GAMEPAD_A` |
| `b` | Red action button | B | `XUSB_GAMEPAD_B` |
| `x` | Blue action button | X | `XUSB_GAMEPAD_X` |
| `y` | Yellow action button | Y | `XUSB_GAMEPAD_Y` |
| `left_bumper` | Left shoulder button | LB | `XUSB_GAMEPAD_LEFT_SHOULDER` |
| `right_bumper` | Right shoulder button | RB | `XUSB_GAMEPAD_RIGHT_SHOULDER` |
| `back` | Back/select button | Back | `XUSB_GAMEPAD_BACK` |
| `start` | Start button | Start | `XUSB_GAMEPAD_START` |
| `left_thumb` | Left stick click | L3 | `XUSB_GAMEPAD_LEFT_THUMB` |
| `right_thumb` | Right stick click | R3 | `XUSB_GAMEPAD_RIGHT_THUMB` |

**Type:** `bool`
**Valid values:** `True` (pressed), `False` (released)
**Default:** `False`

### 2. D-Pad (Boolean)

Directional pad represented as four separate boolean fields.

| Field | Description | Xbox 360 Button | vgamepad Constant |
|-------|-------------|-----------------|-------------------|
| `dpad_up` | D-pad up direction | D-Pad Up | `XUSB_GAMEPAD_DPAD_UP` |
| `dpad_down` | D-pad down direction | D-Pad Down | `XUSB_GAMEPAD_DPAD_DOWN` |
| `dpad_left` | D-pad left direction | D-Pad Left | `XUSB_GAMEPAD_DPAD_LEFT` |
| `dpad_right` | D-pad right direction | D-Pad Right | `XUSB_GAMEPAD_DPAD_RIGHT` |

**Type:** `bool`
**Valid values:** `True` (pressed), `False` (released)
**Default:** `False`

**Note:** Only one D-pad direction should be active at a time in normal usage, though the protocol allows multiple directions simultaneously.

### 3. Analog Triggers (Float)

Analog trigger values representing depression depth.

| Field | Description | Range | vgamepad API |
|-------|-------------|-------|--------------|
| `left_trigger` | Left analog trigger (LT) | 0.0 to 1.0 | `left_trigger(0-255)` |
| `right_trigger` | Right analog trigger (RT) | 0.0 to 1.0 | `right_trigger(0-255)` |

**Type:** `float`
**Valid range:** `0.0` (fully released) to `1.0` (fully pressed)
**Default:** `0.0`
**Backend conversion:** Multiplied by 255 for vgamepad (0-255 integer)

### 4. Analog Sticks (Float)

Two-axis analog joystick positions.

| Field | Description | Range | vgamepad API |
|-------|-------------|-------|--------------|
| `left_stick_x` | Left stick horizontal axis | -1.0 to 1.0 | `left_joystick(x, y)` |
| `left_stick_y` | Left stick vertical axis | -1.0 to 1.0 | `left_joystick(x, y)` |
| `right_stick_x` | Right stick horizontal axis | -1.0 to 1.0 | `right_joystick(x, y)` |
| `right_stick_y` | Right stick vertical axis | -1.0 to 1.0 | `right_joystick(x, y)` |

**Type:** `float`
**Valid range:** `-1.0` (full left/up) to `1.0` (full right/down)
**Default:** `0.0` (center)
**Backend conversion:** Multiplied by 32767 for vgamepad (-32767 to 32767 integer)
**Deadzone:** Applied server-side (configurable, default 0.1)

## Complete GamepadState Definition

```python
@dataclass
class GamepadState:
    """Represents the complete state of a gamepad controller."""
    
    # Digital buttons (Xbox 360 layout)
    a: bool = False
    b: bool = False
    x: bool = False
    y: bool = False
    left_bumper: bool = False
    right_bumper: bool = False
    back: bool = False
    start: bool = False
    left_thumb: bool = False
    right_thumb: bool = False
    
    # D-pad
    dpad_up: bool = False
    dpad_down: bool = False
    dpad_left: bool = False
    dpad_right: bool = False
    
    # Analog triggers
    left_trigger: float = 0.0  # 0.0 to 1.0
    right_trigger: float = 0.0  # 0.0 to 1.0
    
    # Analog sticks
    left_stick_x: float = 0.0  # -1.0 to 1.0
    left_stick_y: float = 0.0  # -1.0 to 1.0
    right_stick_x: float = 0.0  # -1.0 to 1.0
    right_stick_y: float = 0.0  # -1.0 to 1.0
```

## Missing Controls (Not Currently Implemented)

The following Xbox 360 controller buttons are **not** included in `GamepadState`:

### Guide Button
- **Field:** `guide` (not present)
- **Description:** Xbox Guide button (center button with Xbox logo)
- **vgamepad support:** Available as `XUSB_GAMEPAD_GUIDE`
- **Reason for exclusion:** 
  - No function when using Xbox 360 Controller with Windows
  - Primarily used for console-specific features (Xbox menu, pairing)
  - Not typically needed for PC game emulation
- **Consideration for future:** Could be added if PC applications require Xbox Guide functionality

## Backend Compatibility

For detailed backend-specific capabilities, see [BACKEND_CAPABILITIES.md](BACKEND_CAPABILITIES.md).

### vgamepad (PC Server)
- **Status:** ✅ Full support for all current GamepadState fields
- **Emulation types:** Xbox 360, DualShock 4
- **Button mapping:** Direct 1:1 mapping via `XUSB_BUTTON` constants (Xbox 360) or `DS4_BUTTONS` (DualShock 4)
- **Analog conversion:** Server handles float-to-integer conversion
- **Details:** See Xbox 360 and DualShock 4 sections in BACKEND_CAPABILITIES.md

### Android HID (Plug & Play)
- **Status:** ✅ Full support for all current GamepadState fields
- **Report format:** 8-byte HID report (defined in `HidReportBuilder.kt`)
- **Bit mapping:** Buttons packed into 16 bits (2 bytes)
- **Analog conversion:** Client handles float-to-byte conversion
- **Details:** See Bluetooth HID section in BACKEND_CAPABILITIES.md

### Network Protocols (UDP/WebSocket)
- **Status:** ✅ Full support for all current GamepadState fields
- **Serialization:** JSON encoding
- **Transmission:** All fields included in every input packet
- **Gyroscope:** Additional `gyro_x`, `gyro_y`, `gyro_z` fields sent separately (not part of GamepadState)
- **Details:** See UDP and WebSocket sections in BACKEND_CAPABILITIES.md

### USB/ADB
- **Status:** ✅ Full support for all current GamepadState fields
- **Serialization:** JSON encoding (same as network protocols)
- **Transmission:** ADB port forwarding for zero-latency connection
- **Details:** See USB/ADB section in BACKEND_CAPABILITIES.md

## Haptic Commands (Separate from GamepadState)

Haptic feedback is handled separately from gamepad input state:

### VibrationCommand
```python
@dataclass
class VibrationCommand:
    """Represents a vibration command sent to client."""
    client_id: str
    left_motor: float      # 0.0 to 1.0
    right_motor: float     # 0.0 to 1.0
    duration: float        # seconds
    timestamp: datetime
```

**Separation rationale:**
- Haptic commands are output (server → client), not input (client → server)
- Vibration is a time-based event, not a persistent state
- Different transmission requirements (event-driven vs state synchronization)

`VibrationCommand` is the existing haptic representation used by
`HapticFeedbackManager`; this phase does not add a new backend or alter any
transport. Motor values are clamped to `0.0..1.0` and duration to
`0.0..10.0` when a command is triggered.

## Validation Rules

### Input Validation (Server-side)
1. **Boolean fields:** Cast to `bool`, reject non-boolean values
2. **Trigger fields:** Clamp to `[0.0, 1.0]`, reject non-numeric values
3. **Stick fields:** Clamp to `[-1.0, 1.0]`, reject non-numeric values
4. **NaN/Infinity:** Replace with default value (0.0)
5. **Type safety:** Reject invalid types before processing

### Deadzone Application
- Applied server-side in `GamepadEmulator._apply_deadzone()`
- Configurable per stick (default: 0.1)
- Formula: `(value - deadzone) / (1.0 - deadzone)` for values beyond deadzone
- Values within deadzone are set to 0.0

## Neutral State Detection

The `is_neutral()` method returns `True` when all inputs are at default values:

```python
def is_neutral(self) -> bool:
    """Return True if no buttons or analog axes are active."""
    return not (
        self.a or self.b or self.x or self.y or
        self.left_bumper or self.right_bumper or
        self.back or self.start or
        self.left_thumb or self.right_thumb or
        self.dpad_up or self.dpad_down or self.dpad_left or self.dpad_right or
        abs(self.left_trigger) > 0.01 or abs(self.right_trigger) > 0.01 or
        abs(self.left_stick_x) > 0.01 or abs(self.left_stick_y) > 0.01 or
        abs(self.right_stick_x) > 0.01 or abs(self.right_stick_y) > 0.01
    )
```

**Threshold:** 0.01 for analog values to account for floating-point precision

## Usage Examples

### Creating a neutral state
```python
state = GamepadState()  # All fields at defaults
```

### Setting button press
```python
state = GamepadState(a=True, b=False, left_stick_x=0.5)
```

### Updating from network input
```python
input_data = {
    'a': True,
    'left_stick_x': 0.75,
    'right_trigger': 0.5
}
state = GamepadState(**input_data)
```

### Server-side processing
```python
# Clamp and validate values
state.left_trigger = clamp(state.left_trigger, 0.0, 1.0)
state.left_stick_x = clamp(state.left_stick_x, -1.0, 1.0)

# Apply deadzone
left_x = apply_deadzone(state.left_stick_x, deadzone)

# Send to vgamepad
pad.left_joystick(int(left_x * 32767), int(left_y * 32767))
```

## Future Extensions

### Potential additions (not currently planned)
- `guide` button (if PC applications require Xbox Guide functionality)
- Touchpad data (for DualShock 4 emulation)
- Additional sensor data (accelerometer, magnetometer)
- Custom button mappings/profiles

### Extension guidelines
1. Verify backend support (vgamepad, Android HID) before adding
2. Maintain backward compatibility with existing clients
3. Update all protocol implementations (UDP, WebSocket, Bluetooth, USB)
4. Update documentation and examples
5. Add validation rules for new fields

## Version History

- **v1.0** (current): Initial formalization of GamepadState with 20 fields
  - 10 digital buttons
  - 4 D-pad directions
  - 2 analog triggers
  - 4 analog stick axes
