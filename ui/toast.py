import tkinter as tk
import threading
import collections
import os
import pathlib

class Toast(tk.Toplevel):
    """Simple non‑blocking toast notification.
    Displays a short message in the bottom‑right corner of the parent
    window and destroys itself after *duration* milliseconds.
    """
    def __init__(self, master, message: str, duration: int = 3000):
        super().__init__(master)
        self.overrideredirect(True)  # No window decorations
        self.attributes("-topmost", True)
        self.configure(bg="#222222")
        label = tk.Label(self, text=message, bg="#222222", fg="#ffffff",
                         font=("Segoe UI", 9))
        label.pack(ipadx=10, ipady=5)

        # Position toast bottom‑right relative to master
        self.update_idletasks()
        x = master.winfo_rootx() + master.winfo_width() - self.winfo_width() - 20
        y = master.winfo_rooty() + master.winfo_height() - self.winfo_height() - 20
        self.geometry(f"+{x}+{y}")
        # Auto‑close after duration ms
        self.after(duration, self.destroy)
