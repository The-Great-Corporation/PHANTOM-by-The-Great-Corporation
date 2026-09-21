"""
Tests de validation Phase 4 — Multi-profils et format d'échange .phantom
"""

import json
import unittest
from tests.test_phase2_schema import migrate_profile, PRESET_PATH, VALID_ELEMENT_KEYS

class TestPhantomExchangeFormat(unittest.TestCase):

    def test_phantom_export_format_is_valid_json(self):
        """Un fichier .phantom exporté est un JSON valide respectant le schéma version 2."""
        with open(PRESET_PATH, encoding="utf-8") as f:
            preset = json.load(f)

        # Simulation d'un profil exporté sous forme .phantom
        phantom_data = {
            "name": "Custom FPS",
            "version": 1,
            "schema_version": 2,
            "button_mappings": preset["button_mappings"],
            "joystick_settings": preset["joystick_settings"],
            "sensitivity_settings": preset["sensitivity_settings"],
            "deadzone_settings": preset["deadzone_settings"],
            "layout_config": {
                "button_positions": preset["button_positions"],
                "skin": "cyberpunk",
                "background_dim": 0.5
            },
            "gyro_enabled": True,
            "haptic_enabled": True,
            "created_at": 1726900000000,
            "updated_at": 1726900000000
        }

        serialized = json.dumps(phantom_data, indent=2)
        deserialized = json.loads(serialized)

        self.assertEqual(deserialized["schema_version"], 2)
        self.assertEqual(deserialized["name"], "Custom FPS")
        self.assertEqual(deserialized["layout_config"]["skin"], "cyberpunk")
        self.assertEqual(set(deserialized["layout_config"]["button_positions"].keys()), VALID_ELEMENT_KEYS)

    def test_phantom_import_auto_migrates_if_older(self):
        """Un fichier .phantom exporté depuis une ancienne version est automatiquement migré lors de l'import."""
        legacy_phantom = {
            "name": "Old Setup",
            "version": 1,
            # schema_version absent
            "layout_config": {
                "button_positions": {
                    "btn_a": {"x": 0.88, "y": 0.70, "size": 1.0},
                    "btn_b": {"x": 0.92, "y": 0.65, "size": 1.0},
                    "btn_x": {"x": 0.84, "y": 0.65, "size": 1.0},
                    "btn_y": {"x": 0.88, "y": 0.60, "size": 1.0},
                    "left_trigger": {"x": 0.08, "y": 0.12, "size": 1.0}
                }
            }
        }

        migrated = migrate_profile(legacy_phantom)
        self.assertEqual(migrated["schema_version"], 2)
        self.assertIn("abxy", migrated["layout_config"]["button_positions"])
        self.assertIn("btn_lt", migrated["layout_config"]["button_positions"])
        self.assertNotIn("left_trigger", migrated["layout_config"]["button_positions"])

if __name__ == "__main__":
    unittest.main(verbosity=2)
