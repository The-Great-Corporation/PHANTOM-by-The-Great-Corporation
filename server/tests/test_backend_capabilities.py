import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.backend_capabilities import (
    SupportLevel,
    get_all_capabilities,
    get_capability_matrix,
)


class TestBackendCapabilities(unittest.TestCase):
    FEATURES = {
        "vibration",
        "vibration_feedback",
        "led_support",
        "gyro_input",
        "multi_client",
    }

    def test_matrix_uses_explicit_support_levels_for_every_backend(self):
        capabilities = get_all_capabilities()
        matrix = get_capability_matrix()
        self.assertEqual(set(matrix), set(capabilities))
        valid_levels = {level.value for level in SupportLevel}

        for backend_name, row in matrix.items():
            self.assertTrue(row, backend_name)
            self.assertTrue(self.FEATURES.issubset(row), backend_name)
            self.assertTrue(
                all(value in valid_levels for value in row.values()),
                backend_name,
            )

    def test_unimplemented_paths_are_not_promised(self):
        matrix = get_capability_matrix()
        self.assertEqual(matrix["xbox360"]["btn_guide"], "unsupported")
        self.assertEqual(matrix["dualshock4"]["btn_a"], "unsupported")
        self.assertEqual(matrix["dualshock4"]["axis_left_stick_x"], "unsupported")
        self.assertEqual(matrix["udp"]["gyro_input"], "unsupported")
        self.assertEqual(matrix["websocket"]["vibration"], "partial")
        self.assertEqual(matrix["usb_adb"]["vibration_feedback"], "partial")
        self.assertEqual(matrix["bluetooth_rfcomm"]["multi_client"], "unsupported")

    def test_real_input_paths_remain_supported(self):
        matrix = get_capability_matrix()
        for backend in ("xbox360", "bluetooth_hid", "udp", "websocket", "usb_adb"):
            self.assertEqual(matrix[backend]["btn_a"], "supported")
            self.assertEqual(matrix[backend]["axis_left_trigger"], "supported")


if __name__ == "__main__":
    unittest.main()
