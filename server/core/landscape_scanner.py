"""
Landscape Scanner - Pre-flight environment and hardware diagnostic for PHANTOM Server
The Great Corporation - "Satisfaction > Coût (minimal)"
"""

import os
import sys
import socket
import shutil
import logging
import subprocess
from typing import Dict, List, Tuple, Optional, Any
from dataclasses import dataclass, field

logger = logging.getLogger("Phantom.LandscapeScanner")

# Windows Mutex constants
MUTEX_NAME = "Global\\PhantomServerTGC_SingleInstance_Mutex"
ERROR_ALREADY_EXISTS = 183

_PROCESS_MUTEX_HANDLE = None


@dataclass
class LandscapeReport:
    """Consolidated landscape report evaluating the local system before launch."""
    is_single_instance: bool = True
    active_instances_count: int = 0
    vigembus_installed: bool = False
    vigembus_details: str = ""
    ports_status: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    all_ports_available: bool = True
    adb_installed: bool = False
    adb_path: Optional[str] = None
    connected_adb_devices: List[str] = field(default_factory=list)
    primary_ip: str = "127.0.0.1"
    all_ips: List[str] = field(default_factory=list)
    summary_messages: List[str] = field(default_factory=list)

    @property
    def is_launch_ready(self) -> bool:
        """Returns True if the landscape allows launching without critical conflicts."""
        return self.is_single_instance and self.all_ports_available

    def format_cli_summary(self) -> str:
        """Generate a formatted terminal report with TGC branding."""
        lines = [
            "============================================================",
            "   PHANTOM by The Great Corporation - Landscape Diagnostic  ",
            "         « Satisfaction > Cout (minimal) »                  ",
            "============================================================",
        ]

        # Single instance
        status_instance = "OK (Unique)" if self.is_single_instance else "CONFLIT DETECTE (Doublon)"
        lines.append(f"[Anti-Doublon Instance]  : {status_instance}")

        # ViGEmBus
        status_vigem = "OK (Installe & Pret)" if self.vigembus_installed else "ATTENTION (Pilote absent)"
        lines.append(f"[Pilote ViGEmBus PC]     : {status_vigem} - {self.vigembus_details}")

        # Ports
        status_ports = "OK (Tous les ports sont libres)" if self.all_ports_available else "CONFLIT (Port occupe)"
        lines.append(f"[Ports Reseau TGC]       : {status_ports}")
        for name, pinfo in self.ports_status.items():
            st = "Libre" if pinfo["available"] else "OCCUPE !"
            lines.append(f"   -> {name.upper():12s} (Port {pinfo['port']}/{pinfo['proto'].upper()}): {st}")

        # ADB
        status_adb = f"OK ({self.adb_path})" if self.adb_installed else "Non detecte (Optionnel, requis uniquement pour USB)"
        lines.append(f"[Pont USB ADB]           : {status_adb}")
        if self.connected_adb_devices:
            lines.append(f"   -> Appareils USB detectes : {', '.join(self.connected_adb_devices)}")

        # Network
        lines.append(f"[IP LAN Principale]      : {self.primary_ip}")
        if len(self.all_ips) > 1:
            lines.append(f"   -> Adresses detectees   : {', '.join(self.all_ips)}")

        lines.append("------------------------------------------------------------")
        if self.is_launch_ready:
            lines.append(" RESULTAT : FEU VERT - Environnement sain, aucun doublon.")
        else:
            lines.append(" RESULTAT : BLOCAGE OU ATTENTION REQUISE.")
            for msg in self.summary_messages:
                lines.append(f"   * {msg}")
        lines.append("============================================================")
        return "\n".join(lines)


class LandscapeScanner:
    """Scans local hardware, ports, drivers, and process landscape to prevent duplicates."""

    DEFAULT_PORTS = {
        "udp_gamepad": (8888, "udp"),
        "websocket": (8889, "tcp"),
        "usb_adb": (8890, "tcp"),
        "bluetooth": (8887, "tcp"),
    }

    @staticmethod
    def check_single_instance(acquire: bool = False, mutex_name: str = MUTEX_NAME) -> Tuple[bool, Optional[str]]:
        """Verify if another instance of Phantom Server is already running.

        On Windows, this uses a system-wide named Mutex for zero-overhead, atomic detection.
        If acquire is True, the mutex is kept held by this process.
        """
        global _PROCESS_MUTEX_HANDLE

        if sys.platform != "win32":
            # POSIX fallback using lockfile or socket
            return True, "Plateforme non-Windows (Mutex ignore)"

        try:
            import ctypes
            from ctypes import wintypes

            kernel32 = ctypes.windll.kernel32
            CreateMutexW = kernel32.CreateMutexW
            CreateMutexW.argtypes = [wintypes.LPVOID, wintypes.BOOL, wintypes.LPCWSTR]
            CreateMutexW.restype = wintypes.HANDLE
            GetLastError = kernel32.GetLastError

            handle = CreateMutexW(None, False, mutex_name)
            last_error = GetLastError()

            if last_error == ERROR_ALREADY_EXISTS:
                if handle:
                    kernel32.CloseHandle(handle)
                return False, "Une instance de PHANTOM Server tourne deja sur ce PC."


            if acquire:
                _PROCESS_MUTEX_HANDLE = handle
            elif handle:
                kernel32.CloseHandle(handle)

            return True, "Aucune autre instance detectee."
        except Exception as e:
            logger.warning(f"Erreur lors du controle du Mutex : {e}")
            return True, f"Verification Mutex impossible : {e}"

    @staticmethod
    def release_single_instance():
        """Explicitly release the held single instance Mutex."""
        global _PROCESS_MUTEX_HANDLE
        if _PROCESS_MUTEX_HANDLE and sys.platform == "win32":
            try:
                import ctypes
                ctypes.windll.kernel32.CloseHandle(_PROCESS_MUTEX_HANDLE)
            except Exception:
                pass
            _PROCESS_MUTEX_HANDLE = None

    @staticmethod
    def check_vigembus() -> Tuple[bool, str]:
        """Check if ViGEmBus driver is installed on Windows."""
        if sys.platform != "win32":
            return False, "Non applicable hors Windows"

        try:
            import winreg
            key_path = r"SYSTEM\CurrentControlSet\Services\ViGEmBus"
            with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, key_path) as key:
                try:
                    display_name, _ = winreg.QueryValueEx(key, "DisplayName")
                    return True, f"Detecte : {display_name}"
                except FileNotFoundError:
                    return True, "Detecte dans la base de registre"
        except FileNotFoundError:
            pass
        except Exception as e:
            logger.debug(f"Erreur lecture registre ViGEmBus : {e}")

        # Secondary check: attempt vgamepad import & probe if possible
        try:
            import vgamepad as vg
            # If vgamepad is installed, test instantiation
            pad = vg.VX360Gamepad()
            del pad
            return True, "Valide via vgamepad runtime"
        except Exception as e:
            return False, f"Non detecte ({e})"

    @classmethod
    def check_port_availability(cls, port: int, proto: str = "tcp") -> bool:
        """Check if a network port is free to bind."""
        sock_type = socket.SOCK_STREAM if proto.lower() == "tcp" else socket.SOCK_DGRAM
        s = socket.socket(socket.AF_INET, sock_type)
        try:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            s.bind(("0.0.0.0", port))
            s.close()
            return True
        except OSError:
            return False
        finally:
            try:
                s.close()
            except Exception:
                pass

    @classmethod
    def scan_ports(cls, ports_dict: Optional[Dict[str, Tuple[int, str]]] = None) -> Dict[str, Dict[str, Any]]:
        """Check availability of all server ports."""
        target_ports = ports_dict or cls.DEFAULT_PORTS
        results = {}
        for name, (port, proto) in target_ports.items():
            free = cls.check_port_availability(port, proto)
            results[name] = {
                "port": port,
                "proto": proto,
                "available": free,
            }
        return results

    @staticmethod
    def check_adb() -> Tuple[bool, Optional[str], List[str]]:
        """Check if ADB executable exists and find attached devices."""
        adb_path = shutil.which("adb")
        devices = []
        if not adb_path:
            # Check local common paths
            local_adb = os.path.join(os.getcwd(), "adb.exe")
            if os.path.exists(local_adb):
                adb_path = local_adb

        if adb_path:
            try:
                proc = subprocess.run(
                    [adb_path, "devices"],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    timeout=2,
                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0)
                )
                if proc.returncode == 0:
                    for line in proc.stdout.splitlines()[1:]:
                        parts = line.strip().split()
                        if len(parts) >= 2 and parts[1] == "device":
                            devices.append(parts[0])
            except Exception:
                pass

        return (adb_path is not None), adb_path, devices

    @staticmethod
    def detect_ips() -> Tuple[str, List[str]]:
        """Detect primary LAN IP and list all detected non-loopback addresses."""
        detected = []
        primary = "127.0.0.1"

        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.settimeout(0.5)
            s.connect(("8.8.8.8", 80))
            primary = s.getsockname()[0]
            s.close()
            detected.append(primary)
        except Exception:
            pass

        try:
            hostname = socket.gethostname()
            for ip in socket.gethostbyname_ex(hostname)[2]:
                if ip not in detected and not ip.startswith("127."):
                    detected.append(ip)
        except Exception:
            pass

        return primary, detected

    @classmethod
    def run_landscape_scan(cls, acquire_mutex: bool = False) -> LandscapeReport:
        """Execute a full pre-flight scan of the host environment."""
        report = LandscapeReport()

        # 1. Single Instance / Anti-doublon
        is_single, instance_msg = cls.check_single_instance(acquire=acquire_mutex)
        report.is_single_instance = is_single
        if not is_single:
            report.summary_messages.append(instance_msg)

        # 2. ViGEmBus
        vigem_ok, vigem_desc = cls.check_vigembus()
        report.vigembus_installed = vigem_ok
        report.vigembus_details = vigem_desc
        if not vigem_ok:
            report.summary_messages.append(
                "Le pilote ViGEmBus n'est pas installe. L'emulation XInput PC necessite l'installation du pilote ViGEmBus."
            )

        # 3. Network Ports
        ports_res = cls.scan_ports()
        report.ports_status = ports_res
        all_free = all(p["available"] for p in ports_res.values())
        report.all_ports_available = all_free
        if not all_free:
            conflicted = [f"{k} (port {v['port']})" for k, v in ports_res.items() if not v["available"]]
            report.summary_messages.append(f"Conflits de ports detectes : {', '.join(conflicted)}")

        # 4. ADB Bridge
        has_adb, adb_p, devices = cls.check_adb()
        report.adb_installed = has_adb
        report.adb_path = adb_p
        report.connected_adb_devices = devices

        # 5. IP Addresses
        primary_ip, all_ips = cls.detect_ips()
        report.primary_ip = primary_ip
        report.all_ips = all_ips

        return report


if __name__ == "__main__":
    report = LandscapeScanner.run_landscape_scan()
    print(report.format_cli_summary())
