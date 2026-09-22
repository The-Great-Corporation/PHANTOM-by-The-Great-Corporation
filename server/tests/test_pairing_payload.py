import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.pairing_payload import generate_pairing_payload
from core.session_security import SessionSecurity


class TestPairingPayload(unittest.TestCase):
    def test_payload_is_versioned_and_contains_expiration_without_logging_secret(self):
        security = SessionSecurity(clock=lambda: 100.0)
        result = generate_pairing_payload(
            security, "phone-a", "192.168.1.20", 8888, ttl_seconds=60, clock=lambda: 1000.0
        )
        document = json.loads(result.payload)
        self.assertEqual(document["scheme"], "phantom-pairing")
        self.assertEqual(document["version"], 1)
        self.assertEqual(document["expires_at"], 1060)
        self.assertEqual(document["token_secret"], result.token.value)
        self.assertNotIn(result.token.value, repr(result.token))

    def test_invalid_server_and_port_are_rejected(self):
        security = SessionSecurity()
        with self.assertRaises(ValueError):
            generate_pairing_payload(security, "phone-a", "udp://host", 8888)
        with self.assertRaises(ValueError):
            generate_pairing_payload(security, "phone-a", "host", 0)


if __name__ == "__main__":
    unittest.main()
