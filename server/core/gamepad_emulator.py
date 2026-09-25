"""
Gamepad Emulator - Handles gamepad emulation using vgamepad with multi-slot and watchdog support
"""

import asyncio
import logging
import math
import time
from typing import Dict, List, Callable, Any, Mapping, Optional, TypedDict
from dataclasses import dataclass

try:
    import vgamepad as vg
except ImportError:
    vg = None
    logging.warning("vgamepad not installed. Gamepad emulation will not work.")

logger = logging.getLogger(__name__)


ButtonValue = bool
TriggerValue = float
StickValue = float


class GamepadStatePayload(TypedDict, total=False):
    """Wire representation of a gamepad state.

    Network transports may send a partial payload; omitted fields use the
    neutral defaults from :class:`GamepadState`.
    """

    a: ButtonValue
    b: ButtonValue
    x: ButtonValue
    y: ButtonValue
    left_bumper: ButtonValue
    right_bumper: ButtonValue
    back: ButtonValue
    start: ButtonValue
    left_thumb: ButtonValue
    right_thumb: ButtonValue
    dpad_up: ButtonValue
    dpad_down: ButtonValue
    dpad_left: ButtonValue
    dpad_right: ButtonValue
    left_trigger: TriggerValue
    right_trigger: TriggerValue
    left_stick_x: StickValue
    left_stick_y: StickValue
    right_stick_x: StickValue
    right_stick_y: StickValue


@dataclass
class GamepadState:
    """Complete Xbox-compatible gamepad input state.

    Buttons and D-pad directions are booleans. Triggers use ``0.0..1.0`` and
    stick axes use ``-1.0..1.0``. The emulator performs the final runtime
    clamping before sending values to the native backend.
    """
    # Buttons (Xbox 360 layout)
    a: bool = False
    b: bool = False
    x: bool = False
    y: bool = False
    left_bumper: bool = False
    right_bumper: bool = False
    left_trigger: float = 0.0  # 0.0 to 1.0
    right_trigger: float = 0.0  # 0.0 to 1.0
    back: bool = False
    start: bool = False
    left_thumb: bool = False
    right_thumb: bool = False
    
    # D-pad
    dpad_up: bool = False
    dpad_down: bool = False
    dpad_left: bool = False
    dpad_right: bool = False
    
    # Analog sticks
    left_stick_x: float = 0.0  # -1.0 to 1.0
    left_stick_y: float = 0.0  # -1.0 to 1.0
    right_stick_x: float = 0.0  # -1.0 to 1.0
    right_stick_y: float = 0.0  # -1.0 to 1.0

    @classmethod
    def from_mapping(cls, data: Mapping[str, Any]) -> "GamepadState":
        """Build a state from a partial network payload.

        Unknown fields (for example optional sensor values) are ignored so
        existing transports remain forward-compatible.
        """
        fields = {
            "a", "b", "x", "y", "left_bumper", "right_bumper",
            "left_trigger", "right_trigger", "back", "start",
            "left_thumb", "right_thumb", "dpad_up", "dpad_down",
            "dpad_left", "dpad_right", "left_stick_x", "left_stick_y",
            "right_stick_x", "right_stick_y",
        }
        return cls(**{key: data[key] for key in fields if key in data})

    def to_payload(self) -> GamepadStatePayload:
        """Return the state using the stable 20-field wire contract."""
        return {
            "a": self.a,
            "b": self.b,
            "x": self.x,
            "y": self.y,
            "left_bumper": self.left_bumper,
            "right_bumper": self.right_bumper,
            "back": self.back,
            "start": self.start,
            "left_thumb": self.left_thumb,
            "right_thumb": self.right_thumb,
            "dpad_up": self.dpad_up,
            "dpad_down": self.dpad_down,
            "dpad_left": self.dpad_left,
            "dpad_right": self.dpad_right,
            "left_trigger": self.left_trigger,
            "right_trigger": self.right_trigger,
            "left_stick_x": self.left_stick_x,
            "left_stick_y": self.left_stick_y,
            "right_stick_x": self.right_stick_x,
            "right_stick_y": self.right_stick_y,
        }

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


class GamepadEmulator:
    """Emulates virtual gamepads using vgamepad with multi-controller, vibration and watchdog support."""
    
    def __init__(self, config: dict):
        self.config = config
        self.gamepads: Dict[int, Any] = {}
        self.client_slots: Dict[str, int] = {}
        self.slot_states: Dict[int, GamepadState] = {}
        self.slot_last_input: Dict[int, float] = {}
        self.vibration_listeners: List[Callable[[int, float, float], None]] = []
        self._notification_callbacks: Dict[int, Any] = {}
        
        self.emulation_type = config.get('gamepad', {}).get('emulation_type', 'xbox360').lower()
        self.deadzone_left = config.get('gamepad', {}).get('deadzone_left', 0.1)
        self.deadzone_right = config.get('gamepad', {}).get('deadzone_right', 0.1)
        self.vibration_enabled = config.get('gamepad', {}).get('vibration_enabled', True)
        self.input_timeout = config.get('gamepad', {}).get('input_timeout', 1.0)
        
        self._watchdog_task: Optional[asyncio.Task] = None
        self._initialized = False

    @property
    def gamepad(self):
        """Backward-compatible property referencing Player 1."""
        return self.gamepads.get(1)

    @gamepad.setter
    def gamepad(self, val):
        if val is not None:
            self.gamepads[1] = val
        elif 1 in self.gamepads:
            del self.gamepads[1]

    @property
    def current_state(self) -> GamepadState:
        """Backward-compatible property referencing Player 1 state."""
        return self.slot_states.get(1, GamepadState())

    @current_state.setter
    def current_state(self, val: GamepadState):
        self.slot_states[1] = val
        
    def _create_pad_instance(self, slot: int):
        """Instantiate a virtual gamepad for a given slot."""
        if vg is None:
            return None
        try:
            if self.emulation_type in ['dualshock4', 'ds4', 'ps4']:
                pad = vg.VDS4Gamepad()
                logger.info(f"DualShock 4 virtual gamepad initialized for slot {slot}")
            else:
                pad = vg.VX360Gamepad()
                if self.vibration_enabled:
                    def _on_vib(client, target, large_motor, small_motor, led_number, user_data):
                        self._handle_vibration_notification(slot, large_motor, small_motor)
                    pad.register_notification(_on_vib)
                    self._notification_callbacks[slot] = _on_vib
                logger.info(f"Xbox 360 virtual gamepad initialized for slot {slot}")
            return pad
        except Exception as e:
            logger.error(f"Failed to create gamepad for slot {slot}: {e}")
            return None

    def _handle_vibration_notification(self, slot: int, large_motor: int, small_motor: int):
        """Dispatch native rumble notifications from Windows games."""
        left = large_motor / 255.0
        right = small_motor / 255.0
        logger.debug(f"Native XInput vibration [Slot {slot}]: L={left:.2f}, R={right:.2f}")
        for listener in self.vibration_listeners:
            try:
                res = listener(slot, left, right)
                if asyncio.iscoroutine(res):
                    asyncio.create_task(res)
            except Exception as e:
                logger.error(f"Error in vibration listener: {e}")

    def register_vibration_listener(self, listener: Callable[[int, float, float], None]):
        """Register a callback for vibration events: listener(slot, left_motor, right_motor)."""
        self.vibration_listeners.append(listener)

    async def initialize(self):
        """Initialize the gamepad emulator."""
        if vg is None:
            logger.error("vgamepad library not available. Cannot initialize gamepad.")
            return
        
        try:
            primary_pad = self._create_pad_instance(1)
            if primary_pad:
                self.gamepads[1] = primary_pad
                self.slot_states[1] = GamepadState()
                self.slot_last_input[1] = time.time()
                self._initialized = True
                logger.info("Primary virtual gamepad (Slot 1) initialized successfully")
            else:
                self._initialized = False

            if self._initialized:
                self._watchdog_task = asyncio.create_task(self._watchdog_loop())
        except Exception as e:
            logger.error(f"Failed to initialize gamepad emulator: {e}")
            self._initialized = False
    
    async def cleanup(self):
        """Clean up all gamepad resources."""
        if self._watchdog_task:
            self._watchdog_task.cancel()
            try:
                await self._watchdog_task
            except asyncio.CancelledError:
                pass
            self._watchdog_task = None

        for slot, pad in list(self.gamepads.items()):
            try:
                self._reset_gamepad(pad)
                pad.update()
                logger.info(f"Gamepad slot {slot} cleaned up")
            except Exception as e:
                logger.error(f"Error during cleanup of slot {slot}: {e}")
        
        self.gamepads.clear()
        self.client_slots.clear()
        self.slot_states.clear()
        self.slot_last_input.clear()
        self._notification_callbacks.clear()
        self._initialized = False

    def get_slot_for_client(self, client_id: Optional[str]) -> int:
        """Assign or retrieve an XInput slot (1..4) for a given client."""
        if not client_id:
            return 1
        if client_id in self.client_slots:
            return self.client_slots[client_id]
        
        occupied = set(self.client_slots.values())
        for slot in range(1, 5):
            if slot not in occupied:
                self.client_slots[client_id] = slot
                if slot not in self.gamepads and self._initialized:
                    pad = self._create_pad_instance(slot)
                    if pad:
                        self.gamepads[slot] = pad
                self.slot_states[slot] = GamepadState()
                self.slot_last_input[slot] = time.time()
                logger.info(f"Assigned Player {slot} to client {client_id}")
                return slot
        
        logger.warning(f"All 4 player slots occupied. Routing client {client_id} to Slot 1")
        self.client_slots[client_id] = 1
        return 1

    def release_client(self, client_id: str):
        """Release the gamepad slot assigned to a client and reset inputs."""
        if client_id in self.client_slots:
            slot = self.client_slots.pop(client_id)
            logger.info(f"Released Player {slot} for disconnected client {client_id}")
            self.reset_slot(slot)

    def reset_slot(self, slot: int):
        """Reset inputs on a specific slot to neutral."""
        pad = self.gamepads.get(slot)
        if pad:
            try:
                self._reset_gamepad(pad)
                pad.update()
            except Exception as e:
                logger.error(f"Error resetting gamepad slot {slot}: {e}")
        self.slot_states[slot] = GamepadState()
        self.slot_last_input[slot] = time.time()

    def reset_client_state(self, client_id: Optional[str] = None):
        """Reset the gamepad associated with a client (or Player 1 if None)."""
        slot = self.client_slots.get(client_id, 1) if client_id else 1
        self.reset_slot(slot)

    async def _watchdog_loop(self):
        """Periodically check for inactive clients holding buttons and reset to neutral."""
        while True:
            try:
                await asyncio.sleep(0.2)
                now = time.time()
                for slot, pad in list(self.gamepads.items()):
                    last_time = self.slot_last_input.get(slot, 0)
                    state = self.slot_states.get(slot)
                    if state and not state.is_neutral():
                        if (now - last_time) > self.input_timeout:
                            logger.info(f"Watchdog: Player {slot} inactive for >{self.input_timeout}s with active inputs. Auto-resetting to neutral.")
                            self.reset_slot(slot)
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in gamepad watchdog loop: {e}")
    
    def _reset_gamepad(self, pad=None):
        """Reset all gamepad inputs to default state."""
        pad = pad or self.gamepad
        if not pad or vg is None:
            return
            
        if hasattr(pad, 'reset'):
            pad.reset()
        else:
            pad.left_trigger(0)
            pad.right_trigger(0)
            pad.left_joystick(0, 0)
            pad.right_joystick(0, 0)
    
    def _apply_deadzone(self, value: float, deadzone: float) -> float:
        """Apply deadzone to analog input."""
        if abs(value) < deadzone:
            return 0.0
        scale = 1.0 / (1.0 - deadzone)
        return (value - (deadzone if value > 0 else -deadzone)) * scale

    @staticmethod
    def _clamp_axis(value, minimum: float, maximum: float) -> float:
        """Return a finite numeric controller value within its protocol range."""
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            return 0.0
        value = float(value)
        if not math.isfinite(value):
            return 0.0
        return max(minimum, min(maximum, value))
    
    async def update_state(self, state: GamepadState, client_id: Optional[str] = None):
        """Update the gamepad state from input data."""
        if not self._initialized:
            logger.warning("Gamepad not initialized, cannot update state")
            return
        
        slot = self.get_slot_for_client(client_id)
        pad = self.gamepads.get(slot)
        if not pad:
            pad = self._create_pad_instance(slot)
            if pad:
                self.gamepads[slot] = pad
            else:
                logger.warning(f"Cannot update state: no pad for slot {slot}")
                return
        
        try:
            state.left_trigger = self._clamp_axis(state.left_trigger, 0.0, 1.0)
            state.right_trigger = self._clamp_axis(state.right_trigger, 0.0, 1.0)
            state.left_stick_x = self._clamp_axis(state.left_stick_x, -1.0, 1.0)
            state.left_stick_y = self._clamp_axis(state.left_stick_y, -1.0, 1.0)
            state.right_stick_x = self._clamp_axis(state.right_stick_x, -1.0, 1.0)
            state.right_stick_y = self._clamp_axis(state.right_stick_y, -1.0, 1.0)
            
            # Update buttons
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_A, state.a)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_B, state.b)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_X, state.x)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_Y, state.y)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER, state.left_bumper)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER, state.right_bumper)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK, state.back)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_START, state.start)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB, state.left_thumb)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB, state.right_thumb)
            
            # Update D-pad
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP, state.dpad_up)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN, state.dpad_down)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT, state.dpad_left)
            self._update_button(pad, vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT, state.dpad_right)
            
            # Update triggers (0-255)
            pad.left_trigger(int(state.left_trigger * 255))
            pad.right_trigger(int(state.right_trigger * 255))
            
            # Update analog sticks with deadzone
            left_x = self._apply_deadzone(state.left_stick_x, self.deadzone_left)
            left_y = self._apply_deadzone(state.left_stick_y, self.deadzone_left)
            right_x = self._apply_deadzone(state.right_stick_x, self.deadzone_right)
            right_y = self._apply_deadzone(state.right_stick_y, self.deadzone_right)
            
            pad.left_joystick(int(left_x * 32767), int(left_y * 32767))
            pad.right_joystick(int(right_x * 32767), int(right_y * 32767))
            
            pad.update()
            self.slot_states[slot] = state
            self.slot_last_input[slot] = time.time()
            
        except Exception as e:
            logger.error(f"Error updating gamepad state (slot {slot}): {e}")
    
    def _update_button(self, pad, button, pressed: bool):
        """Update a single button state on given pad."""
        if not pad or vg is None:
            return
        if pressed:
            pad.press_button(button)
        else:
            pad.release_button(button)
    
    async def set_vibration(self, left_motor: float, right_motor: float, slot: int = 1):
        """Set vibration intensity (0.0 to 1.0) and dispatch to listeners."""
        if not self._initialized or not self.vibration_enabled:
            return
        
        self._handle_vibration_notification(slot, int(left_motor * 255), int(right_motor * 255))
    
    def get_current_state(self, slot: int = 1) -> GamepadState:
        """Get the current gamepad state for a given slot (default Player 1)."""
        return self.slot_states.get(slot, GamepadState())
