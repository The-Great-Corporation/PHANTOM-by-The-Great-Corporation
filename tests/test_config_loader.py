import unittest
import os
from config_loader import load_config

class TestConfigLoader(unittest.TestCase):
    def test_load_default_config(self):
        config = load_config()
        self.assertIsNotNone(config)
        self.assertTrue(hasattr(config, "theme"))
        self.assertTrue(hasattr(config, "language"))

if __name__ == '__main__':
    unittest.main()
