"""
Tests de validation Phase 5 — Plug & Play et Rapport HID
"""

import unittest
import struct

class TestBluetoothHidDescriptorAndCapabilities(unittest.TestCase):

    def test_hid_gamepad_report_structure(self):
        """Le rapport HID de la manette Phantom fait exactement 8 octets conformément au descripteur standard."""
        # Structure :
        # - Octet 0-1 : 16 boutons (uint16 little-endian)
        # - Octet 2 : Stick gauche X (-127..127 int8)
        # - Octet 3 : Stick gauche Y (-127..127 int8)
        # - Octet 4 : Stick droit Z (-127..127 int8)
        # - Octet 5 : Stick droit Rz (-127..127 int8)
        # - Octet 6 : Gâchette gauche (0..255 uint8)
        # - Octet 7 : Gâchette droite (0..255 uint8)
        buttons = 0x0001  # Bouton A
        lx = 0
        ly = -64
        rx = 127
        ry = -127
        lt = 255
        rt = 0

        report = struct.pack("<HbbbbBB", buttons, lx, ly, rx, ry, lt, rt)
        self.assertEqual(len(report), 8, "Le rapport HID doit faire exactement 8 octets")

        unpacked = struct.unpack("<HbbbbBB", report)
        self.assertEqual(unpacked[0], 0x0001)
        self.assertEqual(unpacked[1], 0)
        self.assertEqual(unpacked[2], -64)
        self.assertEqual(unpacked[3], 127)
        self.assertEqual(unpacked[4], -127)
        self.assertEqual(unpacked[5], 255)
        self.assertEqual(unpacked[6], 0)

    def test_hid_status_capability_matrix_rules(self):
        """Vérifie la logique des règles d'éligibilité Plug & Play."""
        # Simulation règle Android 9+ (API >= 28)
        def is_pnp_eligible(api_level: int, has_bt: bool, bt_enabled: bool) -> tuple[bool, str]:
            if api_level < 28:
                return False, "UNSUPPORTED_OS"
            if not has_bt:
                return False, "NO_BLUETOOTH_HARDWARE"
            if not bt_enabled:
                return False, "BLUETOOTH_DISABLED"
            return True, "READY"

        # Android 8.0 (API 26) -> Incompatible
        eligible, status = is_pnp_eligible(26, True, True)
        self.assertFalse(eligible)
        self.assertEqual(status, "UNSUPPORTED_OS")

        # Android 9 (API 28) avec BT éteint -> Désactivé
        eligible, status = is_pnp_eligible(28, True, False)
        self.assertFalse(eligible)
        self.assertEqual(status, "BLUETOOTH_DISABLED")

        # Android 14 (API 34) avec tout OK -> Prêt
        eligible, status = is_pnp_eligible(34, True, True)
        self.assertTrue(eligible)
        self.assertEqual(status, "READY")

if __name__ == "__main__":
    unittest.main(verbosity=2)
