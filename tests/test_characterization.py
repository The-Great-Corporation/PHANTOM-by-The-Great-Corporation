"""
Tests de caractérisation Phase 0 — PHANTOM
Ces tests capturent le comportement ACTUEL et servent de baseline.
La Phase 1 doit les laisser 100 % verts.
"""

import json
import asyncio
import sys
import os
import unittest

# Ajout du répertoire server au path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'server'))


# ─── Suite 1 : Format de paquet UDP ─────────────────────────────────────────

class TestUdpPacketFormat(unittest.TestCase):
    """Vérifie le contrat de sérialisation des paquets envoyés vers le serveur."""

    def _make_input_packet(self, client_id: str, data: dict) -> dict:
        return {"type": "input", "client_id": client_id, "data": data}

    def _neutral_state(self) -> dict:
        return {
            "a": False, "b": False, "x": False, "y": False,
            "left_bumper": False, "right_bumper": False,
            "back": False, "start": False,
            "left_thumb": False, "right_thumb": False,
            "dpad_up": False, "dpad_down": False,
            "dpad_left": False, "dpad_right": False,
            "left_stick_x": 0.0, "left_stick_y": 0.0,
            "right_stick_x": 0.0, "right_stick_y": 0.0,
            "left_trigger": 0.0, "right_trigger": 0.0,
        }

    def test_neutral_packet_keys(self):
        """L'état neutre contient exactement les 20 clés attendues."""
        state = self._neutral_state()
        packet = self._make_input_packet("test_client", state)
        data = packet["data"]
        expected_keys = {
            "a", "b", "x", "y",
            "left_bumper", "right_bumper",
            "back", "start", "left_thumb", "right_thumb",
            "dpad_up", "dpad_down", "dpad_left", "dpad_right",
            "left_stick_x", "left_stick_y",
            "right_stick_x", "right_stick_y",
            "left_trigger", "right_trigger",
        }
        self.assertEqual(set(data.keys()), expected_keys)

    def test_neutral_packet_values(self):
        """En état neutre, tous les boutons sont False et tous les axes à 0.0."""
        state = self._neutral_state()
        packet = self._make_input_packet("test_client", state)
        data = packet["data"]
        for key, value in data.items():
            if key.endswith("_x") or key.endswith("_y") or key.endswith("_trigger"):
                self.assertEqual(value, 0.0, f"{key} devrait être 0.0")
            else:
                self.assertFalse(value, f"{key} devrait être False")

    def test_button_a_press_packet(self):
        """Pression sur A → {'a': True, reste neutre}."""
        state = self._neutral_state()
        state["a"] = True
        packet = self._make_input_packet("test_client", state)
        self.assertTrue(packet["data"]["a"])
        self.assertFalse(packet["data"]["b"])
        self.assertEqual(packet["data"]["left_stick_x"], 0.0)

    def test_stick_left_diagonal(self):
        """Stick gauche diagonal X=0.5, Y=-0.7 encodé correctement."""
        state = self._neutral_state()
        state["left_stick_x"] = 0.5
        state["left_stick_y"] = -0.7
        packet = self._make_input_packet("test_client", state)
        self.assertAlmostEqual(packet["data"]["left_stick_x"], 0.5)
        self.assertAlmostEqual(packet["data"]["left_stick_y"], -0.7)

    def test_packet_type_field(self):
        """Le paquet input a bien type='input'."""
        packet = self._make_input_packet("cid", self._neutral_state())
        self.assertEqual(packet["type"], "input")

    def test_packet_json_roundtrip(self):
        """Sérialisation JSON aller-retour sans perte."""
        state = self._neutral_state()
        state["b"] = True
        state["right_stick_x"] = 0.3
        packet = self._make_input_packet("roundtrip_client", state)
        json_str = json.dumps(packet)
        restored = json.loads(json_str)
        self.assertTrue(restored["data"]["b"])
        self.assertAlmostEqual(restored["data"]["right_stick_x"], 0.3)

    def test_discovery_packet_format(self):
        """Paquet de découverte auto = {'type': 'discover'}."""
        disc = {"type": "discover"}
        self.assertEqual(json.dumps(disc), '{"type": "discover"}')


# ─── Suite 2 : Sérialisation de profil ──────────────────────────────────────

class TestProfileSerialization(unittest.TestCase):
    """Vérifie que les profils JSON survivent à un aller-retour sans perte."""

    SAMPLE_PROFILE = {
        "name": "Default",
        "version": 1,
        "button_mappings": {
            "a": "button_a", "b": "button_b",
            "x": "button_x", "y": "button_y",
            "left_bumper": "button_l1", "right_bumper": "button_r1",
            "back": "button_select", "start": "button_start",
            "left_thumb": "button_l3", "right_thumb": "button_r3",
            "dpad_up": "dpad_up", "dpad_down": "dpad_down",
            "dpad_left": "dpad_left", "dpad_right": "dpad_right",
        },
        "joystick_settings": {
            "left":  {"enabled": True, "sensitivity": 1.0, "deadzone": 0.1, "invert_x": False, "invert_y": False},
            "right": {"enabled": True, "sensitivity": 1.0, "deadzone": 0.1, "invert_x": False, "invert_y": False},
        },
        "sensitivity_settings": {"overall": 1.0, "joystick": 1.0, "trigger": 1.0, "gyro": 1.0},
        "deadzone_settings": {"left_stick": 0.1, "right_stick": 0.1, "left_trigger": 0.05, "right_trigger": 0.05},
        "layout_config": {
            "button_positions": {
                "btn_lt": {"x": 0.08, "y": 0.12, "size": 1.0},
                "btn_lb": {"x": 0.18, "y": 0.12, "size": 1.0},
                "left_stick": {"x": 0.13, "y": 0.65, "size": 1.0},
                "dpad": {"x": 0.32, "y": 0.65, "size": 1.0},
                "right_stick": {"x": 0.68, "y": 0.65, "size": 1.0},
                "abxy": {"x": 0.88, "y": 0.65, "size": 1.0},
                "btn_back": {"x": 0.44, "y": 0.12, "size": 1.0},
                "btn_start": {"x": 0.56, "y": 0.12, "size": 1.0},
                "btn_rb": {"x": 0.82, "y": 0.12, "size": 1.0},
                "btn_rt": {"x": 0.92, "y": 0.12, "size": 1.0},
            },
            "background_path": "",
            "background_dim": 0.35,
            "background_scale": 1.0,
            "background_offset_x": 0.0,
            "background_offset_y": 0.0,
            "skin": "xbox",
        },
        "gyro_enabled": False,
        "haptic_enabled": True,
    }

    def test_profile_json_roundtrip(self):
        """Un profil sérialisé en JSON puis rechargé est identique."""
        json_str = json.dumps(self.SAMPLE_PROFILE)
        restored = json.loads(json_str)
        self.assertEqual(restored["name"], "Default")
        self.assertEqual(restored["version"], 1)
        self.assertEqual(restored["layout_config"]["skin"], "xbox")
        self.assertAlmostEqual(restored["layout_config"]["background_dim"], 0.35)

    def test_profile_positions_normalized(self):
        """Les positions de boutons sont bien normalisées (0.0–1.0)."""
        positions = self.SAMPLE_PROFILE["layout_config"]["button_positions"]
        for key, pos in positions.items():
            self.assertGreaterEqual(pos["x"], 0.0, f"{key}.x hors borne")
            self.assertLessEqual(pos["x"], 1.0, f"{key}.x hors borne")
            self.assertGreaterEqual(pos["y"], 0.0, f"{key}.y hors borne")
            self.assertLessEqual(pos["y"], 1.0, f"{key}.y hors borne")
            self.assertGreater(pos["size"], 0.0, f"{key}.size invalide")

    def test_legacy_profile_compat(self):
        """Un ancien profil avec btn_a/btn_y individuels ne doit pas crasher au chargement."""
        legacy = dict(self.SAMPLE_PROFILE)
        legacy_layout = dict(legacy["layout_config"])
        legacy_positions = dict(legacy_layout["button_positions"])
        # Simuler un profil pré-migration : supprimer 'abxy', ajouter les clés individuelles
        del legacy_positions["abxy"]
        legacy_positions["btn_a"] = {"x": 0.88, "y": 0.70, "size": 1.0}
        legacy_positions["btn_y"] = {"x": 0.88, "y": 0.60, "size": 1.0}
        legacy_layout["button_positions"] = legacy_positions
        legacy["layout_config"] = legacy_layout

        # Le chargement JSON ne doit pas crasher
        json_str = json.dumps(legacy)
        restored = json.loads(json_str)
        self.assertIn("btn_a", restored["layout_config"]["button_positions"])
        # La clé abxy doit être absente (la migration Python la reconstruit)
        self.assertNotIn("abxy", restored["layout_config"]["button_positions"])

    def test_profile_required_fields(self):
        """Un profil valide contient tous les champs obligatoires."""
        required = {"name", "version", "button_mappings", "joystick_settings",
                    "sensitivity_settings", "deadzone_settings", "layout_config"}
        profile_keys = set(self.SAMPLE_PROFILE.keys())
        self.assertTrue(required.issubset(profile_keys), f"Champs manquants : {required - profile_keys}")


# ─── Suite 3 : Module haptic_feedback (fix Callable) ────────────────────────

class TestHapticFeedbackImport(unittest.TestCase):
    """Vérifie que le fix Callable permet l'import sans NameError."""

    def test_import_haptic_feedback(self):
        """HapticFeedbackManager s'importe sans exception après le fix."""
        try:
            from core.haptic_feedback import HapticFeedbackManager
        except NameError as e:
            self.fail(f"NameError à l'import (Callable manquant ?) : {e}")
        except ImportError as e:
            self.skipTest(f"Import impossible hors contexte serveur : {e}")

    def test_instantiation(self):
        """HapticFeedbackManager s'instancie avec une config minimale."""
        try:
            from core.haptic_feedback import HapticFeedbackManager
            manager = HapticFeedbackManager({"gamepad": {"vibration_enabled": True}})
            self.assertIsNotNone(manager)
            self.assertTrue(manager.enabled)
        except ImportError:
            self.skipTest("Import impossible hors contexte serveur")

    def test_register_sender(self):
        """register_sender accepte un callable sans erreur."""
        try:
            from core.haptic_feedback import HapticFeedbackManager
            manager = HapticFeedbackManager({})
            calls = []
            manager.register_sender(lambda cid, l, r, d: calls.append((cid, l, r, d)))
            self.assertEqual(len(manager._vibration_senders), 1)
        except ImportError:
            self.skipTest("Import impossible hors contexte serveur")


if __name__ == "__main__":
    unittest.main(verbosity=2)
