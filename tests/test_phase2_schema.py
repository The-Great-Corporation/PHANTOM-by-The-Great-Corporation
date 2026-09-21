"""
Tests de caractérisation Phase 2 — PHANTOM
Vérifie la migration schéma v1→v2, la lecture du preset JSON et le contrat de schemaVersion.
Ces tests s'exécutent en Python pur — ils testent la logique de migration et le format JSON,
sans dépendre du SDK Android.
"""

import json
import copy
import unittest


PRESET_PATH = "android/app/src/main/assets/presets/manette.json"

VALID_ELEMENT_KEYS = {
    "btn_lt", "btn_lb", "btn_back", "btn_start",
    "btn_rb", "btn_rt", "left_stick", "dpad",
    "right_stick", "abxy"
}


# ─── Logique de migration v1 → v2 (portage Python de ProfileManager.migrateIfNeeded) ─

PIXEL_THRESHOLD = 2.0
LEGACY_REF_W = 1280.0
LEGACY_REF_H = 720.0


def migrate_profile(profile: dict) -> dict:
    """Portage Python de ProfileManager.migrateIfNeeded."""
    if profile.get("schema_version", 0) >= 2:
        return profile

    profile = copy.deepcopy(profile)
    positions = dict(profile.get("layout_config", {}).get("button_positions", {}))

    # Étape 1 : normalisation pixel
    needs_norm = any(
        p.get("x", 0) > PIXEL_THRESHOLD or p.get("y", 0) > PIXEL_THRESHOLD
        for p in positions.values()
    )
    if needs_norm:
        for key, pos in positions.items():
            x = pos["x"] / LEGACY_REF_W if pos["x"] > PIXEL_THRESHOLD else pos["x"]
            y = pos["y"] / LEGACY_REF_H if pos["y"] > PIXEL_THRESHOLD else pos["y"]
            size = pos["size"] / 50.0 if pos.get("size", 1.0) > PIXEL_THRESHOLD else pos.get("size", 1.0)
            positions[key] = {"x": max(0.0, min(1.0, x)), "y": max(0.0, min(1.0, y)), "size": max(0.5, min(3.0, size))}

    # Étape 2 : clés legacy → abxy
    if "abxy" not in positions:
        legacy_keys = ["btn_a", "btn_b", "btn_x", "btn_y"]
        legacy_positions = [positions[k] for k in legacy_keys if k in positions]
        if legacy_positions:
            cx = sum(p["x"] for p in legacy_positions) / len(legacy_positions)
            cy = sum(p["y"] for p in legacy_positions) / len(legacy_positions)
            sz = sum(p.get("size", 1.0) for p in legacy_positions) / len(legacy_positions)
            positions["abxy"] = {"x": cx, "y": cy, "size": sz}
            for k in legacy_keys:
                positions.pop(k, None)

    # Étape 3 : renommage triggers
    for old, new in [("left_trigger", "btn_lt"), ("right_trigger", "btn_rt")]:
        if old in positions and new not in positions:
            positions[new] = positions.pop(old)
        elif old in positions:
            positions.pop(old)

    profile["schema_version"] = 2
    profile.setdefault("layout_config", {})["button_positions"] = positions
    return profile


# ─── Suite 1 : Preset JSON ────────────────────────────────────────────────────

class TestPresetJson(unittest.TestCase):

    def setUp(self):
        with open(PRESET_PATH, encoding="utf-8") as f:
            self.preset = json.load(f)

    def test_schema_version_is_2(self):
        """Le preset a schema_version=2."""
        self.assertEqual(self.preset["schema_version"], 2)

    def test_preset_id_present(self):
        """Le preset a un preset_id non vide."""
        self.assertTrue(self.preset.get("preset_id"), "preset_id manquant")

    def test_all_element_keys_present(self):
        """Les 10 clés d'éléments obligatoires sont présentes dans button_positions."""
        positions = set(self.preset["button_positions"].keys())
        self.assertEqual(positions, VALID_ELEMENT_KEYS,
                         f"Clés manquantes/inattendues : {positions ^ VALID_ELEMENT_KEYS}")

    def test_positions_normalized(self):
        """Toutes les positions sont normalisées 0–1."""
        for key, pos in self.preset["button_positions"].items():
            self.assertGreaterEqual(pos["x"], 0.0, f"{key}.x < 0")
            self.assertLessEqual(pos["x"], 1.0, f"{key}.x > 1")
            self.assertGreaterEqual(pos["y"], 0.0, f"{key}.y < 0")
            self.assertLessEqual(pos["y"], 1.0, f"{key}.y > 1")
            self.assertGreater(pos["size"], 0.0, f"{key}.size invalide")

    def test_button_mappings_present(self):
        """Le preset contient les 14 mappings de boutons."""
        expected = {
            "a", "b", "x", "y", "left_bumper", "right_bumper",
            "back", "start", "left_thumb", "right_thumb",
            "dpad_up", "dpad_down", "dpad_left", "dpad_right"
        }
        actual = set(self.preset.get("button_mappings", {}).keys())
        self.assertEqual(actual, expected, f"Mappings manquants : {expected - actual}")

    def test_skin_field(self):
        """Le preset a un champ skin non vide."""
        self.assertTrue(self.preset.get("skin"), "Champ skin manquant")

    def test_joystick_settings(self):
        """left et right sont présents dans joystick_settings."""
        js = self.preset.get("joystick_settings", {})
        self.assertIn("left", js)
        self.assertIn("right", js)

    def test_json_round_trip(self):
        """Le preset survit à un aller-retour JSON sans perte."""
        restored = json.loads(json.dumps(self.preset))
        self.assertEqual(restored["schema_version"], self.preset["schema_version"])
        self.assertEqual(
            set(restored["button_positions"].keys()),
            set(self.preset["button_positions"].keys())
        )


# ─── Suite 2 : Migration v1 → v2 ─────────────────────────────────────────────

class TestProfileMigration(unittest.TestCase):

    def _v1_profile(self, positions=None, schema_version=None):
        p = {
            "name": "Legacy",
            "version": 1,
            "button_mappings": {},
            "layout_config": {
                "button_positions": positions or {}
            }
        }
        if schema_version is not None:
            p["schema_version"] = schema_version
        return p

    def test_v2_profile_not_migrated(self):
        """Un profil schemaVersion=2 est retourné intact."""
        profile = self._v1_profile()
        profile["schema_version"] = 2
        result = migrate_profile(profile)
        self.assertEqual(result["schema_version"], 2)
        self.assertIs(result, profile)  # même objet, non copié

    def test_missing_schema_version_triggers_migration(self):
        """Un profil sans schema_version est traité comme v1 et migré."""
        profile = self._v1_profile()
        result = migrate_profile(profile)
        self.assertEqual(result["schema_version"], 2)

    def test_pixel_positions_normalized(self):
        """Les positions pixel (> 2.0) sont normalisées 0–1."""
        profile = self._v1_profile({
            "btn_lt": {"x": 102.4, "y": 86.4, "size": 50.0},  # 102.4/1280 = 0.08, 86.4/720 = 0.12
        })
        result = migrate_profile(profile)
        pos = result["layout_config"]["button_positions"]["btn_lt"]
        self.assertAlmostEqual(pos["x"], 0.08, places=3)
        self.assertAlmostEqual(pos["y"], 0.12, places=3)

    def test_normalized_positions_unchanged(self):
        """Les positions déjà normalisées (≤ 2.0) ne sont pas touchées."""
        profile = self._v1_profile({
            "btn_lt": {"x": 0.08, "y": 0.12, "size": 1.0},
        })
        result = migrate_profile(profile)
        pos = result["layout_config"]["button_positions"]["btn_lt"]
        self.assertAlmostEqual(pos["x"], 0.08, places=5)
        self.assertAlmostEqual(pos["y"], 0.12, places=5)

    def test_legacy_abxy_keys_merged(self):
        """Les clés btn_a/b/x/y individuelles sont fusionnées en abxy."""
        profile = self._v1_profile({
            "btn_a": {"x": 0.88, "y": 0.70, "size": 1.0},
            "btn_b": {"x": 0.92, "y": 0.65, "size": 1.0},
            "btn_x": {"x": 0.84, "y": 0.65, "size": 1.0},
            "btn_y": {"x": 0.88, "y": 0.60, "size": 1.0},
        })
        result = migrate_profile(profile)
        positions = result["layout_config"]["button_positions"]
        self.assertIn("abxy", positions)
        # Les clés individuelles doivent avoir disparu
        for legacy in ["btn_a", "btn_b", "btn_x", "btn_y"]:
            self.assertNotIn(legacy, positions, f"{legacy} devrait être supprimée")
        # Le centroïde X doit être la moyenne de 0.88, 0.92, 0.84, 0.88 = 0.88
        self.assertAlmostEqual(positions["abxy"]["x"], 0.88, places=5)

    def test_abxy_key_not_merged_if_already_present(self):
        """Si abxy existe déjà, les clés legacy btn_a etc. ne déclenchent pas de fusion."""
        profile = self._v1_profile({
            "abxy": {"x": 0.88, "y": 0.65, "size": 1.0},
            "btn_a": {"x": 0.50, "y": 0.50, "size": 1.0},  # doit être ignoré
        })
        result = migrate_profile(profile)
        # abxy est conservé tel quel
        self.assertAlmostEqual(result["layout_config"]["button_positions"]["abxy"]["x"], 0.88)

    def test_trigger_keys_renamed(self):
        """left_trigger/right_trigger sont renommés en btn_lt/btn_rt."""
        profile = self._v1_profile({
            "left_trigger":  {"x": 0.08, "y": 0.12, "size": 1.0},
            "right_trigger": {"x": 0.92, "y": 0.12, "size": 1.0},
        })
        result = migrate_profile(profile)
        positions = result["layout_config"]["button_positions"]
        self.assertIn("btn_lt", positions)
        self.assertIn("btn_rt", positions)
        self.assertNotIn("left_trigger", positions)
        self.assertNotIn("right_trigger", positions)

    def test_migration_idempotent(self):
        """Migrer deux fois un profil donne le même résultat que migrer une fois."""
        profile = self._v1_profile({
            "btn_a": {"x": 0.88, "y": 0.70, "size": 1.0},
        })
        once = migrate_profile(profile)
        twice = migrate_profile(copy.deepcopy(once))
        self.assertEqual(
            once["layout_config"]["button_positions"],
            twice["layout_config"]["button_positions"]
        )
        self.assertEqual(once["schema_version"], twice["schema_version"])


# ─── Suite 3 : Contrat schemaVersion dans les nouveaux profils ────────────────

class TestSchemaVersionContract(unittest.TestCase):

    def test_preset_is_schema_v2(self):
        """Le preset manette.json est schemaVersion=2."""
        with open(PRESET_PATH, encoding="utf-8") as f:
            preset = json.load(f)
        self.assertEqual(preset["schema_version"], 2)

    def test_migrated_profile_has_schema_v2(self):
        """Tout profil migré reçoit schemaVersion=2."""
        profile = {"name": "Old", "version": 1, "layout_config": {"button_positions": {}}}
        result = migrate_profile(profile)
        self.assertEqual(result["schema_version"], 2)

    def test_v2_profile_positions_are_valid_element_keys(self):
        """Un profil v2 complet n'a que des clés parmi les 10 éléments valides."""
        with open(PRESET_PATH, encoding="utf-8") as f:
            preset = json.load(f)
        unknown = set(preset["button_positions"].keys()) - VALID_ELEMENT_KEYS
        self.assertEqual(unknown, set(), f"Clés inconnues dans le preset : {unknown}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
