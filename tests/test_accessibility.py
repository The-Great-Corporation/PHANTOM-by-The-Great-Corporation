import unittest
import tkinter as tk
from tkinter import ttk
from server.gui_app import PhantomServerApp
class TestAccessibility(unittest.TestCase):
    def setUp(self):
        self.root = tk.Tk()
        self.app = PhantomServerApp(self.root)

    def tearDown(self):
        self.root.destroy()

    def test_widgets_takefocus(self):
        self.assertEqual(str(self.app.start_btn.cget('takefocus')), '1')
        self.assertEqual(str(self.app.notebook.cget('takefocus')), '1')
        self.assertEqual(str(self.app.clients_tree.cget('takefocus')), '1')

if __name__ == '__main__':
    unittest.main()
