import unittest
import tkinter as tk
from ui.toast import Toast

class TestToast(unittest.TestCase):
    def test_toast_creation(self):
        root = tk.Tk()
        toast = Toast(root, "Test Message", duration=100)
        self.assertIsNotNone(toast)
        labels = [c for c in toast.winfo_children() if isinstance(c, tk.Label)]
        self.assertEqual(labels[0].cget("text"), "Test Message")
        root.update()
        root.destroy()

if __name__ == '__main__':
    unittest.main()
