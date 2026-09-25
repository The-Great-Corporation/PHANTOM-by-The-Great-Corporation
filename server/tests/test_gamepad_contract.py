import os
import sys
import unittest
from dataclasses import fields
from typing import get_type_hints

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.gamepad_emulator import GamepadState, GamepadStatePayload
from core.haptic_feedback import VibrationCommand


class TestGamepadStateContract(unittest.TestCase):
    EXPECTED_FIELDS = {
        "a", "b", "x", "y", "left_bumper", "right_bumper",
        "back", "start", "left_thumb", "right_thumb",
        "dpad_up", "dpad_down", "dpad_left", "dpad_right",
        "left_trigger", "right_trigger",
        "left_stick_x", "left_stick_y", "right_stick_x", "right_stick_y",
    }

    def test_neutral_state_defaults_and_types(self):
        state = GamepadState()
        self.assertEqual({field.name for field in fields(state)}, self.EXPECTED_FIELDS)
        for name in (
            "a", "b", "x", "y", "left_bumper", "right_bumper",
            "back", "start", "left_thumb", "right_thumb",
            "dpad_up", "dpad_down", "dpad_left", "dpad_right",
        ):
            self.assertIs(type(getattr(state, name)), bool)
            self.assertFalse(getattr(state, name))
        for name in (
            "left_trigger", "right_trigger", "left_stick_x", "left_stick_y",
            "right_stick_x", "right_stick_y",
        ):
            self.assertIs(type(getattr(state, name)), float)
            self.assertEqual(getattr(state, name), 0.0)

    def test_payload_roundtrip_preserves_legacy_fields(self):
        state = GamepadState(a=True, left_trigger=0.5, right_stick_y=-1.0)
        payload = state.to_payload()
        restored = GamepadState.from_mapping({**payload, "gyro_x": 0.25})
        self.assertEqual(restored, state)
        self.assertEqual(set(payload), self.EXPECTED_FIELDS)

    def test_payload_type_contract_is_explicit(self):
        annotations = get_type_hints(GamepadStatePayload)
        self.assertEqual(set(annotations), self.EXPECTED_FIELDS)
        for name in ("a", "dpad_up", "left_bumper"):
            self.assertIs(annotations[name], bool)
        for name in ("left_trigger", "right_trigger", "left_stick_x", "right_stick_y"):
            self.assertIs(annotations[name], float)

    def test_haptics_are_not_part_of_gamepad_state(self):
        self.assertNotIn("left_motor", self.EXPECTED_FIELDS)
        self.assertNotIn("duration", self.EXPECTED_FIELDS)
        self.assertEqual(
            {field.name for field in fields(VibrationCommand)},
            {"client_id", "left_motor", "right_motor", "duration", "timestamp"},
        )


if __name__ == "__main__":
    unittest.main()
