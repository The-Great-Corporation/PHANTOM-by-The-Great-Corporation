#!/usr/bin/env python3
"""
Phantom Server Suite - by The Great Corporation
Modern Windows GUI for Server Management & Live Gamepad Visualizer
"The controller you don't hold, the power you command"
"""

import sys
import os
import socket
import asyncio
import threading
import json
import logging
from datetime import datetime
import tkinter as tk
from tkinter import ttk, messagebox
import collections
import pathlib

# Load configuration and translations
from config_loader import load_config

def load_translations(lang: str):
    i18n_path = pathlib.Path(__file__).parent.parent / 'i18n' / f"{lang}.json"
    if i18n_path.is_file():
        with open(i18n_path, encoding='utf-8') as f:
            return json.load(f)
    return {}

AppConfig = load_config()
Translations = load_translations(AppConfig.language)

# Import custom toast notification
from ui.toast import Toast

import collections
import pathlib




# Ensure current dir is in sys.path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from main import VirtualGamepadServer
from core.gamepad_emulator import GamepadState

# Colors - TGC Dark Theme
BG_COLOR = "#0A0C11"
CARD_BG = "#151821"
CARD_BORDER = "#252A3A"
ACCENT_PRIMARY = "#00D2FF"      # TGC Cyan
ACCENT_GOLD = "#FFB800"         # TGC Gold Accent
TEXT_PRIMARY = "#FFFFFF"
TEXT_SECONDARY = "#8E95A5"
SUCCESS_COLOR = "#00E676"
ERROR_COLOR = "#FF5252"
BTN_HOVER = "#00B4DB"

def get_local_ip():
    """Detect local LAN IPv4 address."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0.5)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


class TextLogHandler(logging.Handler):
    """Logging handler that redirects logs to a Tkinter Text widget.
    It keeps only the last ``AppConfig.log_retention`` lines in the widget
    and appends older lines to ``logs/server.log``.
    """
    def __init__(self, text_widget):
        super().__init__()
        self.text_widget = text_widget
        self.buffer = collections.deque(maxlen=AppConfig.log_retention)
        self.log_file = pathlib.Path(__file__).parent.parent / "logs" / "server.log"
        self.log_file.parent.mkdir(parents=True, exist_ok=True)

    def emit(self, record):
        msg = self.format(record)
        # Append to buffer; if buffer is full, the leftmost entry will be dropped on next append.
        if len(self.buffer) == AppConfig.log_retention:
            # Write the oldest line to file before it gets discarded.
            oldest = self.buffer[0]
            with open(self.log_file, "a", encoding="utf-8") as f:
                f.write(oldest + "\n")
        self.buffer.append(msg)
        # Refresh UI widget on the main thread.
        def refresh():
            self.text_widget.configure(state='normal')
            self.text_widget.delete('1.0', tk.END)
            self.text_widget.insert(tk.END, "\n".join(self.buffer))
            self.text_widget.see(tk.END)
            self.text_widget.configure(state='disabled')
        self.text_widget.after(0, refresh)


class PhantomServerApp:
    def __init__(self, root):
        self.root = root
        self.root.title(Translations["title"])
        self.root.geometry("960x680")
        self.root.minsize(900, 620)
        self.root.configure(bg=BG_COLOR)

        self.server = None
        self.server_thread = None
        self.server_loop = None
        self.is_running = False
        self.local_ip = get_local_ip()

        self.last_state = GamepadState()

        self._setup_styles()
        self._build_header()
        self._build_tabs()
        self._build_footer()

        # Periodic UI update for controller visualizer
        self.root.after(33, self._update_visualizer_loop)

    def _setup_styles(self):
        style = ttk.Style()
        style.theme_use('clam')
        style.configure(".", background=BG_COLOR, foreground=TEXT_PRIMARY, font=("Segoe UI", 10))
        style.configure("TNotebook", background=BG_COLOR, borderwidth=0)
        style.configure("TNotebook.Tab", background=CARD_BG, foreground=TEXT_SECONDARY, padding=[20, 10], font=("Segoe UI", 10, "bold"))
        style.map("TNotebook.Tab", background=[("selected", ACCENT_PRIMARY)], foreground=[("selected", "#000000")])

    def _build_header(self):
        header_frame = tk.Frame(self.root, bg=BG_COLOR, pady=12, padx=24)
        header_frame.pack(fill=tk.X)

        title_box = tk.Frame(header_frame, bg=BG_COLOR)
        title_box.pack(side=tk.LEFT)

        title = tk.Label(title_box, text="PHANTOM", font=("Segoe UI", 24, "bold"), fg=ACCENT_PRIMARY, bg=BG_COLOR)
        title.pack(anchor="w")

        subtitle = tk.Label(title_box, text="by The Great Corporation", font=("Segoe UI", 10, "italic"), fg=ACCENT_GOLD, bg=BG_COLOR)
        subtitle.pack(anchor="w")

        status_box = tk.Frame(header_frame, bg=BG_COLOR)
        status_box.pack(side=tk.RIGHT)

        help_btn = tk.Button(status_box, text=Translations.get("help_button", "Help"), font=("Segoe UI", 9, "bold"),
                             bg=CARD_BG, fg=TEXT_PRIMARY, bd=1, relief="solid", command=self._show_help, takefocus=True)
        help_btn.pack(side=tk.LEFT, padx=(0, 20))

        self.status_dot = tk.Label(status_box, text="●", font=("Segoe UI", 16), fg=ERROR_COLOR, bg=BG_COLOR)
        self.status_dot.pack(side=tk.LEFT, padx=6)

        self.status_label = tk.Label(status_box, text=Translations["status_stopped"], font=("Segoe UI", 11, "bold"), fg=TEXT_SECONDARY, bg=BG_COLOR)
        self.status_label.pack(side=tk.LEFT)

    def _build_tabs(self):
        self.notebook = ttk.Notebook(self.root, takefocus=True)
        self.notebook.pack(fill=tk.BOTH, expand=True, padx=20, pady=5)

        # Tab 1: Dashboard & Connection
        self.tab_dashboard = tk.Frame(self.notebook, bg=BG_COLOR)
        self.notebook.add(self.tab_dashboard, text=Translations["dashboard_tab"])
        self._build_dashboard_tab()

        # Tab 2: Live Gamepad Visualizer & Logs
        self.tab_visualizer = tk.Frame(self.notebook, bg=BG_COLOR)
        self.notebook.add(self.tab_visualizer, text=Translations["visualizer_tab"])
        self._build_visualizer_tab()

    def _build_dashboard_tab(self):
        grid_frame = tk.Frame(self.tab_dashboard, bg=BG_COLOR, padx=10, pady=10)
        grid_frame.pack(fill=tk.BOTH, expand=True)

        left_col = tk.Frame(grid_frame, bg=BG_COLOR)
        left_col.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(0, 10))

        # Card 1: IP & Quick Connect
        ip_card = tk.LabelFrame(left_col, text=" Détection Réseau Phantom ", bg=CARD_BG, fg=ACCENT_PRIMARY,
                                font=("Segoe UI", 11, "bold"), padx=16, pady=16, bd=1, relief="solid")
        ip_card.pack(fill=tk.X, pady=(0, 15))

        tk.Label(ip_card, text="Adresse IP locale diffusée aux smartphones (Auto-Discovery actif) :", bg=CARD_BG, fg=TEXT_SECONDARY, font=("Segoe UI", 9)).pack(anchor="w")

        ip_box = tk.Frame(ip_card, bg=CARD_BG, pady=8)
        ip_box.pack(fill=tk.X)

        self.ip_entry = tk.Entry(ip_box, font=("Consolas", 16, "bold"), bg="#0E1015", fg=SUCCESS_COLOR,
                                 bd=0, relief="flat", justify="center")
        self.ip_entry.insert(0, self.local_ip)
        self.ip_entry.configure(state="readonly")
        self.ip_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, ipady=6)

        copy_btn = tk.Button(ip_box, text=Translations["copy_button"], font=("Segoe UI", 9, "bold"), bg=ACCENT_PRIMARY, fg="#000",
                             bd=0, activebackground=BTN_HOVER, padx=12, command=self._copy_ip, takefocus=True)
        copy_btn.pack(side=tk.LEFT, padx=(8, 0), ipady=6)

        # Card 2: Server Ports & Services
        services_card = tk.LabelFrame(left_col, text=" Canaux de Diffusion ", bg=CARD_BG, fg=ACCENT_PRIMARY,
                                      font=("Segoe UI", 11, "bold"), padx=16, pady=16, bd=1, relief="solid")
        services_card.pack(fill=tk.X, pady=(0, 15))

        self.proto_labels = {}
        protocols = [
            ("Wi-Fi UDP (Auto-Discovery actif)", "Port 8888", "udp"),
            ("Wi-Fi WebSocket (Mode secours)", "Port 8889", "ws"),
            ("Câble USB (ADB Reverse)", "Port 8890", "usb"),
            ("Bluetooth RFCOMM", "Port 8887", "bt"),
        ]
        for name, port, key in protocols:
            row = tk.Frame(services_card, bg=CARD_BG, pady=4)
            row.pack(fill=tk.X)
            tk.Label(row, text=f"• {name}", bg=CARD_BG, fg=TEXT_PRIMARY, font=("Segoe UI", 9)).pack(side=tk.LEFT)
            lbl = tk.Label(row, text=f"[{port}] Inactif", bg=CARD_BG, fg=TEXT_SECONDARY, font=("Segoe UI", 9, "bold"))
            lbl.pack(side=tk.RIGHT)
            self.proto_labels[key] = lbl

        btn_frame = tk.Frame(left_col, bg=BG_COLOR, pady=10)
        btn_frame.pack(fill=tk.X)

        self.start_btn = tk.Button(btn_frame, text=Translations["start_server"], font=("Segoe UI", 12, "bold"),
                                   bg=SUCCESS_COLOR, fg="#000000", bd=0, relief="flat", pady=10,
                                   command=self.toggle_server, takefocus=True)
        self.start_btn.pack(fill=tk.X)

        # Right Column: Connected Clients
        right_col = tk.Frame(grid_frame, bg=BG_COLOR)
        right_col.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True, padx=(10, 0))

        clients_card = tk.LabelFrame(right_col, text=" Contrôleurs Phantom Connectés ", bg=CARD_BG, fg=ACCENT_PRIMARY,
                                     font=("Segoe UI", 11, "bold"), padx=16, pady=16, bd=1, relief="solid")
        clients_card.pack(fill=tk.BOTH, expand=True)

        self.clients_tree = ttk.Treeview(clients_card, columns=("ID", "Protocole", "Adresse", "Latence"), show="headings", height=8, takefocus=True)
        self.clients_tree.heading("ID", text="Appareil")
        self.clients_tree.heading("Protocole", text="Protocole")
        self.clients_tree.heading("Adresse", text="Adresse IP")
        self.clients_tree.heading("Latence", text="Latence")
        self.clients_tree.column("ID", width=120)
        self.clients_tree.column("Protocole", width=90)
        self.clients_tree.column("Adresse", width=130)
        self.clients_tree.column("Latence", width=80)
        self.clients_tree.pack(fill=tk.BOTH, expand=True)

    def _build_visualizer_tab(self):
        split_frame = tk.Frame(self.tab_visualizer, bg=BG_COLOR, padx=10, pady=10)
        split_frame.pack(fill=tk.BOTH, expand=True)

        vis_frame = tk.LabelFrame(split_frame, text=" Visualiseur Phantom en Direct ", bg=CARD_BG, fg=ACCENT_PRIMARY,
                                  font=("Segoe UI", 11, "bold"), padx=12, pady=8, bd=1, relief="solid")
        vis_frame.pack(fill=tk.X, pady=(0, 10))

        self.canvas = tk.Canvas(vis_frame, bg="#0F1117", height=240, bd=0, highlightthickness=0)
        self.canvas.pack(fill=tk.X, expand=True)
        self._draw_gamepad_base()

        logs_frame = tk.LabelFrame(split_frame, text=" Trames Réseau & Événements ", bg=CARD_BG, fg=ACCENT_PRIMARY,
                                   font=("Segoe UI", 11, "bold"), padx=12, pady=8, bd=1, relief="solid")
        logs_frame.pack(fill=tk.BOTH, expand=True)

        self.log_text = tk.Text(logs_frame, bg="#0A0C10", fg="#C8CFDF", font=("Consolas", 9),
                                bd=0, wrap="word", state="disabled")
        self.log_text.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        scrollbar = ttk.Scrollbar(logs_frame, command=self.log_text.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.log_text.config(yscrollcommand=scrollbar.set)

        root_logger = logging.getLogger()
        handler = TextLogHandler(self.log_text)
        handler.setFormatter(logging.Formatter('%(asctime)s - %(levelname)s - %(message)s', datefmt='%H:%M:%S'))
        root_logger.addHandler(handler)

    def _build_footer(self):
        footer = tk.Frame(self.root, bg=BG_COLOR, padx=20, pady=8)
        footer.pack(fill=tk.X, side=tk.BOTTOM)

        slogan = tk.Label(footer, text=Translations["footer_slogan"],
                          font=("Segoe UI", 9, "italic"), fg=TEXT_SECONDARY, bg=BG_COLOR)
        slogan.pack(side=tk.LEFT)

        watermark = tk.Label(footer, text=Translations["footer_watermark"],
                             font=("Segoe UI", 9, "bold"), fg="#4B5263", bg=BG_COLOR)
        watermark.pack(side=tk.RIGHT)

    def _copy_ip(self):
        self.root.clipboard_clear()
        self.root.clipboard_append(self.local_ip)
        Toast(self.root, Translations["ip_copied"], duration=AppConfig.toast_duration)

    def _show_help(self):
        help_win = tk.Toplevel(self.root)
        help_win.title(Translations.get("help_title", "Help"))
        help_win.geometry("600x500")
        help_win.configure(bg=BG_COLOR)
        
        text_widget = tk.Text(help_win, bg=CARD_BG, fg=TEXT_PRIMARY, font=("Segoe UI", 10), wrap="word", padx=10, pady=10)
        text_widget.pack(fill=tk.BOTH, expand=True, padx=20, pady=20)
        
        try:
            with open(os.path.join(os.path.dirname(os.path.dirname(__file__)), "docs", "user_guide.md"), "r", encoding="utf-8") as f:
                content = f.read()
            text_widget.insert("1.0", content)
        except Exception as e:
            text_widget.insert("1.0", f"Error loading help: {e}")
            
        text_widget.configure(state="disabled")

    def _draw_gamepad_base(self):
        self.canvas.delete("all")
        w, h = 900, 240
        cx, cy = w // 2, h // 2

        self.canvas.create_polygon(
            cx - 240, cy - 60, cx - 180, cy - 80, cx + 180, cy - 80, cx + 240, cy - 60,
            cx + 280, cy + 40, cx + 220, cy + 90, cx + 130, cy + 70, cx - 130, cy + 70,
            cx - 220, cy + 90, cx - 280, cy + 40,
            fill="#1A1E2B", outline="#2F364C", width=2, smooth=True
        )

        dpx, dpy = cx - 150, cy + 15
        self.canvas.create_rectangle(dpx - 12, dpy - 36, dpx + 12, dpy + 36, fill="#12151E", outline="#3E455B", tags="dpad_base")
        self.canvas.create_rectangle(dpx - 36, dpy - 12, dpx + 36, dpy + 12, fill="#12151E", outline="#3E455B", tags="dpad_base")

        self.canvas.create_polygon(dpx, dpy - 30, dpx - 8, dpy - 16, dpx + 8, dpy - 16, fill="#4A526A", tags="dpad_up")
        self.canvas.create_polygon(dpx, dpy + 30, dpx - 8, dpy + 16, dpx + 8, dpy + 16, fill="#4A526A", tags="dpad_down")
        self.canvas.create_polygon(dpx - 30, dpy, dpx - 16, dpy - 8, dpx - 16, dpy + 8, fill="#4A526A", tags="dpad_left")
        self.canvas.create_polygon(dpx + 30, dpy, dpx + 16, dpy - 8, dpx + 16, dpy + 8, fill="#4A526A", tags="dpad_right")

        abx, aby = cx + 150, cy - 10
        self.btn_coords = {
            "btn_a": (abx, aby + 28, "#2ECC71", "A"),
            "btn_b": (abx + 28, aby, "#E74C3C", "B"),
            "btn_x": (abx - 28, aby, "#3498DB", "X"),
            "btn_y": (abx, aby - 28, "#F1C40F", "Y"),
        }
        for tag, (bx, by, col, label) in self.btn_coords.items():
            self.canvas.create_oval(bx - 14, by - 14, bx + 14, by + 14, fill="#131620", outline=col, width=2, tags=tag)
            self.canvas.create_text(bx, by, text=label, fill=col, font=("Segoe UI", 10, "bold"), tags=tag + "_text")

        self.stick_l_pos = (cx - 70, cy + 25)
        self.stick_r_pos = (cx + 70, cy + 25)

        for sx, sy, tag in [(*self.stick_l_pos, "stick_l"), (*self.stick_r_pos, "stick_r")]:
            self.canvas.create_oval(sx - 30, sy - 30, sx + 30, sy + 30, fill="#0F1219", outline="#2F3547", width=2)
            self.canvas.create_oval(sx - 16, sy - 16, sx + 16, sy + 16, fill="#2F364C", outline="#4B5675", width=2, tags=tag)

        self.canvas.create_rectangle(cx - 200, cy - 75, cx - 120, cy - 60, fill="#242A3C", outline="#414963", tags="btn_lb")
        self.canvas.create_text(cx - 160, cy - 67, text="LB", fill=TEXT_SECONDARY, font=("Segoe UI", 8, "bold"), tags="btn_lb_text")

        self.canvas.create_rectangle(cx + 120, cy - 75, cx + 200, cy - 60, fill="#242A3C", outline="#414963", tags="btn_rb")
        self.canvas.create_text(cx + 160, cy - 67, text="RB", fill=TEXT_SECONDARY, font=("Segoe UI", 8, "bold"), tags="btn_rb_text")

        self.canvas.create_oval(cx - 30, cy - 35, cx - 15, cy - 20, fill="#242A3C", outline="#414963", tags="btn_back")
        self.canvas.create_oval(cx + 15, cy - 35, cx + 30, cy - 20, fill="#242A3C", outline="#414963", tags="btn_start")
        self.canvas.create_text(cx - 22, cy - 27, text="◄", fill="#8E95A5", font=("Segoe UI", 8))
        self.canvas.create_text(cx + 22, cy - 27, text="►", fill="#8E95A5", font=("Segoe UI", 8))

    def _update_visualizer_loop(self):
        if self.server and hasattr(self.server, 'gamepad_emulator'):
            state = self.server.gamepad_emulator.get_current_state()
            self._render_state(state)

            clients = self.server.connection_manager.get_all_clients()
            current_items = self.clients_tree.get_children()
            client_ids = set(clients.keys())

            for item in current_items:
                if item not in client_ids:
                    self.clients_tree.delete(item)

            existing_cids = set(self.clients_tree.get_children())
            for cid, c in clients.items():
                lat_str = f"{c.latency_ms:.1f} ms" if c.latency_ms > 0 else "< 1 ms"
                display_name = "Phantom (" + cid[:12] + ")"
                if cid in existing_cids:
                    self.clients_tree.item(cid, values=(display_name, c.protocol.upper(), c.address, lat_str))
                else:
                    self.clients_tree.insert("", tk.END, iid=cid,
                                             values=(display_name, c.protocol.upper(), c.address, lat_str))

        self.root.after(33, self._update_visualizer_loop)

    def _render_state(self, state: GamepadState):
        button_map = {
            "btn_a": state.a, "btn_b": state.b, "btn_x": state.x, "btn_y": state.y,
            "dpad_up": state.dpad_up, "dpad_down": state.dpad_down,
            "dpad_left": state.dpad_left, "dpad_right": state.dpad_right,
            "btn_lb": state.left_bumper, "btn_rb": state.right_bumper,
            "btn_back": state.back, "btn_start": state.start
        }

        for tag, pressed in button_map.items():
            if tag in self.btn_coords:
                col = self.btn_coords[tag][2]
                self.canvas.itemconfig(tag, fill=col if pressed else "#131620")
            elif "dpad" in tag:
                self.canvas.itemconfig(tag, fill=ACCENT_PRIMARY if pressed else "#4A526A")
            elif tag in ["btn_lb", "btn_rb"]:
                self.canvas.itemconfig(tag, fill=ACCENT_GOLD if pressed else "#242A3C")
            elif tag in ["btn_back", "btn_start"]:
                self.canvas.itemconfig(tag, fill=ACCENT_PRIMARY if pressed else "#242A3C")

        lx = self.stick_l_pos[0] + (state.left_stick_x * 14)
        ly = self.stick_l_pos[1] - (state.left_stick_y * 14)
        self.canvas.coords("stick_l", lx - 16, ly - 16, lx + 16, ly + 16)
        self.canvas.itemconfig("stick_l", fill=ACCENT_PRIMARY if state.left_thumb else "#2F364C")

        rx = self.stick_r_pos[0] + (state.right_stick_x * 14)
        ry = self.stick_r_pos[1] - (state.right_stick_y * 14)
        self.canvas.coords("stick_r", rx - 16, ry - 16, rx + 16, ry + 16)
        self.canvas.itemconfig("stick_r", fill=ACCENT_PRIMARY if state.right_thumb else "#2F364C")

    def toggle_server(self):
        if not self.is_running:
            self._start_server_thread()
        else:
            self._stop_server_thread()

    def _start_server_thread(self):
        self.server = VirtualGamepadServer()
        self.is_running = True

        def run_loop():
            self.server_loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self.server_loop)
            try:
                self.server_loop.run_until_complete(self.server.start())
            except Exception as e:
                logging.error(f"Server error: {e}")
            finally:
                self.server_loop.close()

        self.server_thread = threading.Thread(target=run_loop, daemon=True)
        self.server_thread.start()

        self.status_dot.configure(fg=SUCCESS_COLOR)
        self.status_label.configure(text=Translations["status_running"], fg=SUCCESS_COLOR)
        self.start_btn.configure(text=Translations["stop_server"], bg=ERROR_COLOR)

        for key, lbl in self.proto_labels.items():
            port = "8888" if key == "udp" else ("8889" if key == "ws" else ("8890" if key == "usb" else "8887"))
            lbl.configure(text=f"[{port}] Actif ✔", fg=SUCCESS_COLOR)

        logging.info(Translations["server_started"])

    def _stop_server_thread(self):
        if self.server and self.server_loop:
            asyncio.run_coroutine_threadsafe(self.server.stop(), self.server_loop)

        self.is_running = False
        self.status_dot.configure(fg=ERROR_COLOR)
        self.status_label.configure(text=Translations["status_stopped"], fg=TEXT_SECONDARY)
        self.start_btn.configure(text=Translations["start_server"], bg=SUCCESS_COLOR)

        for key, lbl in self.proto_labels.items():
            port = "8888" if key == "udp" else ("8889" if key == "ws" else ("8890" if key == "usb" else "8887"))
            lbl.configure(text=f"[{port}] Inactif", fg=TEXT_SECONDARY)

        logging.info(Translations["server_stopped"])


def main():
    root = tk.Tk()
    app = PhantomServerApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
