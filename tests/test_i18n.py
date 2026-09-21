import unittest
from server.gui_app import load_translations

class TestI18n(unittest.TestCase):
    def test_load_translations_fr(self):
        translations = load_translations("fr")
        self.assertIsNotNone(translations)
        self.assertIn("dashboard_tab", translations)
        self.assertIn("title", translations)

    def test_load_translations_en(self):
        translations = load_translations("en")
        self.assertIsNotNone(translations)
        self.assertIn("dashboard_tab", translations)
        self.assertIn("title", translations)

if __name__ == '__main__':
    unittest.main()
