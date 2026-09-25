"""Versioned, short-lived QR payloads for the UDP pairing flow."""

import json
import re
import time
from dataclasses import dataclass
from typing import Callable

from .session_security import PairingToken, SessionSecurity

_SERVER_RE = re.compile(r"^[A-Za-z0-9.-]+$")


@dataclass(frozen=True)
class PairingPayload:
    payload: str
    token: PairingToken
    expires_at: int


def generate_pairing_payload(
    security: SessionSecurity,
    device_id: str,
    server: str,
    port: int,
    ttl_seconds: float = 60.0,
    clock: Callable[[], float] = time.time,
) -> PairingPayload:
    """Issue a single-use token and return its QR-safe JSON without logging it."""
    if not isinstance(server, str) or not _SERVER_RE.fullmatch(server) or ".." in server:
        raise ValueError("server must be a hostname or IPv4 address")
    if not isinstance(port, int) or not 1 <= port <= 65535:
        raise ValueError("port must be between 1 and 65535")
    token = security.issue_pairing_token(device_id, ttl_seconds=ttl_seconds)
    expires_at = int(clock() + ttl_seconds)
    document = {
        "scheme": "phantom-pairing",
        "version": 1,
        "server": server,
        "port": port,
        "device_id": device_id,
        "token_id": token.token_id,
        "token_secret": token.value,
        "expires_at": expires_at,
    }
    return PairingPayload(
        payload=json.dumps(document, sort_keys=True, separators=(",", ":")),
        token=token,
        expires_at=expires_at,
    )
