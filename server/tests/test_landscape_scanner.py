"""
Tests for LandscapeScanner pre-flight diagnostics and anti-duplicate features.
The Great Corporation
"""

import sys
import unittest
from unittest.mock import patch, MagicMock
from server.core.landscape_scanner import LandscapeScanner, LandscapeReport


class TestLandscapeScanner(unittest.TestCase):
    def test_scan_ports_format(self):
        ports = {
            "test_port": (9999, "udp")
        }
        res = LandscapeScanner.scan_ports(ports)
        self.assertIn("test_port", res)
        self.assertEqual(res["test_port"]["port"], 9999)
        self.assertEqual(res["test_port"]["proto"], "udp")
        self.assertIsInstance(res["test_port"]["available"], bool)

    def test_single_instance_detection(self):
        # First check should be single instance
        ok, msg = LandscapeScanner.check_single_instance(acquire=False)
        self.assertTrue(ok)

    def test_report_launch_ready_logic(self):
        report = LandscapeReport(
            is_single_instance=True,
            all_ports_available=True
        )
        self.assertTrue(report.is_launch_ready)

        report_conflict_instance = LandscapeReport(
            is_single_instance=False,
            all_ports_available=True
        )
        self.assertFalse(report_conflict_instance.is_launch_ready)

        report_conflict_port = LandscapeReport(
            is_single_instance=True,
            all_ports_available=False
        )
        self.assertFalse(report_conflict_port.is_launch_ready)

    def test_format_cli_summary(self):
        report = LandscapeScanner.run_landscape_scan(acquire_mutex=False)
        summary = report.format_cli_summary()
        self.assertIn("PHANTOM by The Great Corporation", summary)
        self.assertIn("Satisfaction > Cout (minimal)", summary)
        self.assertIn("Anti-Doublon", summary)
        self.assertIn("ViGEmBus", summary)


if __name__ == "__main__":
    unittest.main()
