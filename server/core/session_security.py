"""In-memory primitives for authenticated pairing sessions and remembered enrollment.

This module deliberately does not know about any transport or wire protocol.
Secrets live only in memory and are generated when a pairing token is
consumed or an enrolled device is reconnected.
"""

import hashlib
import hmac
import json
import secrets
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Mapping, Optional, Set


class SessionSecurityError(Exception):
    """Base class for expected session-security rejections."""


class PairingTokenExpiredError(SessionSecurityError):
    """Raised when a pairing token is no longer valid."""


class PairingTokenConsumedError(SessionSecurityError):
    """Raised when a pairing token has already created a session."""


class UnknownPairingTokenError(SessionSecurityError):
    """Raised when a pairing token was not issued by this authority."""


class UnknownSessionError(SessionSecurityError):
    """Raised when an operation references an unknown session."""


class InvalidSequenceError(SessionSecurityError):
    """Raised when a message counter is not a non-negative integer."""


class SequenceAlreadySeenError(SessionSecurityError):
    """Raised when a message counter has already been authenticated."""


class InvalidMacError(SessionSecurityError):
    """Raised when a message MAC does not match the session secret."""


class InvalidPairingChallengeError(SessionSecurityError):
    """Raised when a pairing challenge is unknown, invalid, or already used."""


class UnknownEnrolledDeviceError(SessionSecurityError):
    """Raised when a device is not in the enrolled device registry."""


class EnrolledDeviceExpiredError(SessionSecurityError):
    """Raised when an enrolled device TTL has expired."""


class EnrolledDeviceRevokedError(SessionSecurityError):
    """Raised when an enrolled device has been explicitly revoked."""


class InvalidReconnectChallengeError(SessionSecurityError):
    """Raised when a reconnect challenge is unknown, invalid, expired, or consumed."""


@dataclass(frozen=True)
class PairingToken:
    """Opaque, single-use pairing credential with a monotonic expiry."""

    token_id: str
    value: str = field(repr=False)
    expires_at: float
    device_id: str


@dataclass(frozen=True)
class DeviceIdentity:
    """Stable identity assigned to one authenticated device session."""

    device_id: str
    session_id: str


@dataclass(frozen=True)
class AuthenticatedSession:
    """Authenticated session material held in memory by the server."""

    identity: DeviceIdentity
    created_at: float
    shared_secret: bytes = field(repr=False)


@dataclass
class PairingChallenge:
    """Short-lived server challenge bound to one UDP pairing attempt."""

    challenge_id: str
    challenge: str
    token_id: str
    device_id: str
    client_nonce: str
    address: tuple
    expires_at: float
    consumed: bool = False


@dataclass
class EnrolledDevice:
    """Device enrolled on server after initial QR handshake."""

    device_id: str
    enrollment_secret: str = field(repr=False)
    registered_at: float
    last_activity: float
    is_revoked: bool = False


@dataclass
class ReconnectChallenge:
    """Short-lived server challenge bound to a device reconnection attempt."""

    challenge_id: str
    challenge: str
    device_id: str
    client_nonce: str
    address: tuple
    expires_at: float
    consumed: bool = False


Message = Mapping[str, Any]
Clock = Callable[[], float]


def derive_session_secret(
    token_secret: str,
    client_nonce: str,
    server_challenge: str,
    session_id: str,
) -> bytes:
    """Derive the session key without transmitting it on the wire."""
    if not all(
        isinstance(value, str) and value
        for value in (token_secret, client_nonce, server_challenge, session_id)
    ):
        raise ValueError("session key inputs must be non-empty strings")
    context = json.dumps(
        {
            "client_nonce": client_nonce,
            "server_challenge": server_challenge,
            "session_id": session_id,
        },
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")
    return hmac.new(
        token_secret.encode("utf-8"),
        b"phantom-udp-session-v1\0" + context,
        hashlib.sha256,
    ).digest()


def compute_pairing_proof(
    token_secret: str,
    token_id: str,
    device_id: str,
    client_nonce: str,
    challenge_id: str = None,
    challenge: str = None,
) -> str:
    """Create a pairing proof over the two-step handshake transcript."""
    if not all(
        isinstance(value, str) and value
        for value in (token_secret, token_id, device_id, client_nonce)
    ):
        raise ValueError("pairing proof inputs must be non-empty strings")
    if (challenge_id is None) != (challenge is None):
        raise ValueError("challenge_id and challenge must be provided together")
    transcript = json.dumps(
        {
            "type": "pair_proof" if challenge_id is not None else "pair",
            "token_id": token_id,
            "device_id": device_id,
            "client_nonce": client_nonce,
            **(
                {"challenge_id": challenge_id, "challenge": challenge}
                if challenge_id is not None
                else {}
            ),
        },
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")
    return hmac.new(
        token_secret.encode("utf-8"), transcript, hashlib.sha256
    ).hexdigest()


def compute_reconnect_proof(
    enrollment_secret: str,
    device_id: str,
    client_nonce: str,
    challenge_id: str,
    challenge: str,
) -> str:
    """Create a reconnection proof over the reconnect transcript."""
    if not all(
        isinstance(value, str) and value
        for value in (enrollment_secret, device_id, client_nonce, challenge_id, challenge)
    ):
        raise ValueError("reconnect proof inputs must be non-empty strings")
    transcript = json.dumps(
        {
            "type": "reconnect_proof",
            "device_id": device_id,
            "client_nonce": client_nonce,
            "challenge_id": challenge_id,
            "challenge": challenge,
        },
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")
    return hmac.new(
        enrollment_secret.encode("utf-8"), transcript, hashlib.sha256
    ).hexdigest()


def canonicalize_message(sequence: int, message: Message) -> bytes:
    """Return the deterministic bytes covered by a session MAC."""
    _validate_sequence(sequence)
    if not isinstance(message, Mapping):
        raise TypeError("message must be a mapping")
    try:
        return json.dumps(
            {"message": message, "sequence": sequence},
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
            allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError) as exc:
        raise ValueError("message must contain only canonical JSON values") from exc


class SessionSecurity:
    """Issue pairing tokens, manage device enrollments and authenticate sessions."""

    def __init__(
        self,
        clock: Clock = time.monotonic,
        remembered_device_ttl_seconds: float = 2592000.0,
        reconnect_challenge_ttl_seconds: float = 10.0,
    ):
        self._clock = clock
        self._remembered_device_ttl_seconds = remembered_device_ttl_seconds
        self._reconnect_challenge_ttl_seconds = reconnect_challenge_ttl_seconds
        self._tokens: Dict[str, PairingToken] = {}
        self._consumed_tokens: Set[str] = set()
        self._sessions: Dict[str, AuthenticatedSession] = {}
        self._seen_sequences: Dict[str, Set[int]] = {}
        self._challenges: Dict[str, PairingChallenge] = {}
        self._enrolled_devices: Dict[str, EnrolledDevice] = {}
        self._reconnect_challenges: Dict[str, ReconnectChallenge] = {}

    def issue_pairing_token(self, device_id: str, ttl_seconds: float = 60.0) -> PairingToken:
        if not isinstance(device_id, str) or not device_id:
            raise ValueError("device_id must be a non-empty string")
        if ttl_seconds <= 0:
            raise ValueError("ttl_seconds must be positive")

        token = PairingToken(
            token_id=secrets.token_urlsafe(16),
            value=secrets.token_urlsafe(32),
            expires_at=self._clock() + ttl_seconds,
            device_id=device_id,
        )
        self._tokens[token.token_id] = token
        return token

    def consume_pairing_token(
        self,
        token_id: str,
        proof: str,
        now: float = None,
        device_id: str = None,
        client_nonce: str = None,
        server_challenge: str = None,
    ) -> AuthenticatedSession:
        if not isinstance(token_id, str) or not token_id:
            raise UnknownPairingTokenError("pairing token is unknown")
        if token_id in self._consumed_tokens:
            raise PairingTokenConsumedError("pairing token has already been consumed")
        issued_token = self._tokens.get(token_id)
        if issued_token is None:
            raise UnknownPairingTokenError("pairing token is unknown")
        if not isinstance(device_id, str) or device_id != issued_token.device_id:
            raise UnknownPairingTokenError("pairing token is not valid for this device")
        if not isinstance(proof, str):
            raise InvalidMacError("pairing proof is required")
        expected_proof = compute_pairing_proof(
            issued_token.value, token_id, device_id, client_nonce
        )
        if not hmac.compare_digest(expected_proof, proof):
            raise InvalidMacError("pairing proof does not match")

        current_time = self._clock() if now is None else now
        if current_time >= issued_token.expires_at:
            raise PairingTokenExpiredError("pairing token has expired")

        if not isinstance(client_nonce, str) or not client_nonce:
            raise ValueError("client_nonce is required")
        if not isinstance(server_challenge, str) or not server_challenge:
            raise ValueError("client_nonce and server_challenge must be provided together")
        self._consumed_tokens.add(token_id)
        session_id = secrets.token_urlsafe(24)
        session = AuthenticatedSession(
            identity=DeviceIdentity(
                device_id=issued_token.device_id,
                session_id=session_id,
            ),
            created_at=current_time,
            shared_secret=(
                derive_session_secret(
                    issued_token.value, client_nonce, server_challenge, session_id
                )
                if client_nonce is not None
                else secrets.token_bytes(32)
            ),
        )
        self._sessions[session.identity.session_id] = session
        self._seen_sequences[session.identity.session_id] = set()

        # Enroll device on server
        self._enrolled_devices[issued_token.device_id] = EnrolledDevice(
            device_id=issued_token.device_id,
            enrollment_secret=issued_token.value,
            registered_at=current_time,
            last_activity=current_time,
            is_revoked=False,
        )
        return session

    def create_pairing_challenge(
        self,
        token_id: str,
        device_id: str,
        client_nonce: str,
        address: tuple,
        ttl_seconds: float = 10.0,
    ) -> PairingChallenge:
        """Create a short-lived challenge without consuming the pairing token."""
        if not all(
            isinstance(value, str) and value
            for value in (token_id, device_id, client_nonce)
        ):
            raise ValueError("pairing challenge fields must be non-empty strings")
        if not isinstance(address, tuple) or not address:
            raise ValueError("pairing challenge address is required")
        if ttl_seconds <= 0:
            raise ValueError("challenge ttl must be positive")
        token = self._tokens.get(token_id)
        now = self._clock()
        if token is None:
            raise UnknownPairingTokenError("pairing token is unknown")
        if token_id in self._consumed_tokens:
            raise PairingTokenConsumedError("pairing token has already been consumed")
        if token.device_id != device_id:
            raise UnknownPairingTokenError("pairing token is not valid for this device")
        if now >= token.expires_at:
            raise PairingTokenExpiredError("pairing token has expired")
        challenge = PairingChallenge(
            challenge_id=secrets.token_urlsafe(16),
            challenge=secrets.token_urlsafe(32),
            token_id=token_id,
            device_id=device_id,
            client_nonce=client_nonce,
            address=address,
            expires_at=min(now + ttl_seconds, token.expires_at),
        )
        self._challenges[challenge.challenge_id] = challenge
        return challenge

    def consume_pairing_challenge(
        self,
        challenge_id: str,
        challenge: str,
        token_id: str,
        device_id: str,
        client_nonce: str,
        proof: str,
        address: tuple,
    ) -> AuthenticatedSession:
        """Verify and atomically consume a challenge and its pairing token."""
        pending = self._challenges.get(challenge_id)
        if pending is None or pending.consumed:
            raise InvalidPairingChallengeError("pairing challenge is unknown or consumed")
        if (
            pending.challenge != challenge
            or pending.token_id != token_id
            or pending.device_id != device_id
            or pending.client_nonce != client_nonce
            or pending.address != address
        ):
            raise InvalidPairingChallengeError("pairing challenge binding does not match")
        now = self._clock()
        if now >= pending.expires_at:
            raise InvalidPairingChallengeError("pairing challenge has expired")
        token = self._tokens.get(token_id)
        if token is None:
            raise UnknownPairingTokenError("pairing token is unknown")
        if token_id in self._consumed_tokens:
            raise PairingTokenConsumedError("pairing token has already been consumed")
        if now >= token.expires_at:
            raise PairingTokenExpiredError("pairing token has expired")
        if not isinstance(proof, str):
            raise InvalidMacError("pairing proof is required")
        expected_proof = compute_pairing_proof(
            token.value, token_id, device_id, client_nonce, challenge_id, challenge
        )
        if not hmac.compare_digest(expected_proof, proof):
            raise InvalidMacError("pairing proof does not match")

        pending.consumed = True
        self._consumed_tokens.add(token_id)
        session_id = secrets.token_urlsafe(24)
        session = AuthenticatedSession(
            identity=DeviceIdentity(device_id=device_id, session_id=session_id),
            created_at=now,
            shared_secret=derive_session_secret(
                token.value, client_nonce, challenge, session_id
            ),
        )
        self._sessions[session_id] = session
        self._seen_sequences[session_id] = set()

        # Enroll device on server
        self._enrolled_devices[device_id] = EnrolledDevice(
            device_id=device_id,
            enrollment_secret=token.value,
            registered_at=now,
            last_activity=now,
            is_revoked=False,
        )
        return session

    def create_reconnect_challenge(
        self,
        device_id: str,
        client_nonce: str,
        address: tuple,
        ttl_seconds: Optional[float] = None,
    ) -> ReconnectChallenge:
        """Create a short-lived challenge for an enrolled device reconnection."""
        if not isinstance(device_id, str) or not device_id:
            raise ValueError("device_id must be a non-empty string")
        if not isinstance(client_nonce, str) or not client_nonce:
            raise ValueError("client_nonce must be a non-empty string")
        if not isinstance(address, tuple) or not address:
            raise ValueError("reconnect challenge address is required")

        enrolled = self._enrolled_devices.get(device_id)
        if enrolled is None:
            raise UnknownEnrolledDeviceError("device is not enrolled")
        if enrolled.is_revoked:
            raise EnrolledDeviceRevokedError("device enrollment is revoked")

        now = self._clock()
        if now - enrolled.last_activity > self._remembered_device_ttl_seconds:
            raise EnrolledDeviceExpiredError("device enrollment has expired")

        actual_ttl = ttl_seconds if ttl_seconds is not None else self._reconnect_challenge_ttl_seconds
        if actual_ttl <= 0:
            raise ValueError("challenge ttl must be positive")

        challenge = ReconnectChallenge(
            challenge_id=secrets.token_urlsafe(16),
            challenge=secrets.token_urlsafe(32),
            device_id=device_id,
            client_nonce=client_nonce,
            address=address,
            expires_at=now + actual_ttl,
        )
        self._reconnect_challenges[challenge.challenge_id] = challenge
        return challenge

    def consume_reconnect_challenge(
        self,
        challenge_id: str,
        challenge: str,
        device_id: str,
        client_nonce: str,
        proof: str,
        address: tuple,
    ) -> AuthenticatedSession:
        """Verify reconnection proof and create a new authenticated session."""
        pending = self._reconnect_challenges.get(challenge_id)
        if pending is None or pending.consumed:
            raise InvalidReconnectChallengeError("reconnect challenge is unknown or consumed")
        if (
            pending.challenge != challenge
            or pending.device_id != device_id
            or pending.client_nonce != client_nonce
            or pending.address != address
        ):
            raise InvalidReconnectChallengeError("reconnect challenge binding does not match")

        now = self._clock()
        if now >= pending.expires_at:
            raise InvalidReconnectChallengeError("reconnect challenge has expired")

        enrolled = self._enrolled_devices.get(device_id)
        if enrolled is None:
            raise UnknownEnrolledDeviceError("device is not enrolled")
        if enrolled.is_revoked:
            raise EnrolledDeviceRevokedError("device enrollment is revoked")
        if now - enrolled.last_activity > self._remembered_device_ttl_seconds:
            raise EnrolledDeviceExpiredError("device enrollment has expired")

        if not isinstance(proof, str):
            raise InvalidMacError("reconnect proof is required")

        expected_proof = compute_reconnect_proof(
            enrolled.enrollment_secret, device_id, client_nonce, challenge_id, challenge
        )
        if not hmac.compare_digest(expected_proof, proof):
            raise InvalidMacError("reconnect proof does not match")

        # Mark challenge consumed and update last_activity to now (sliding TTL window pushed forward)
        pending.consumed = True
        enrolled.last_activity = now

        session_id = secrets.token_urlsafe(24)
        session = AuthenticatedSession(
            identity=DeviceIdentity(device_id=device_id, session_id=session_id),
            created_at=now,
            shared_secret=derive_session_secret(
                enrolled.enrollment_secret, client_nonce, challenge, session_id
            ),
        )
        self._sessions[session_id] = session
        self._seen_sequences[session_id] = set()
        return session

    def get_enrolled_device(self, device_id: str) -> Optional[EnrolledDevice]:
        """Get information on an enrolled device."""
        return self._enrolled_devices.get(device_id)

    def revoke_device(self, device_id: str) -> bool:
        """Revoke an enrolled device immediately."""
        enrolled = self._enrolled_devices.get(device_id)
        if enrolled is not None:
            enrolled.is_revoked = True
            return True
        return False

    def compute_mac(self, session_id: str, sequence: int, message: Message) -> bytes:
        session = self._get_session(session_id)
        return hmac.new(
            session.shared_secret,
            canonicalize_message(sequence, message),
            hashlib.sha256,
        ).digest()

    def verify_mac(
        self, session_id: str, sequence: int, message: Message, mac: bytes
    ) -> None:
        session = self._get_session(session_id)
        _validate_sequence(sequence)
        if not isinstance(mac, bytes):
            raise InvalidMacError("MAC must be bytes")
        expected_mac = hmac.new(
            session.shared_secret,
            canonicalize_message(sequence, message),
            hashlib.sha256,
        ).digest()
        if not hmac.compare_digest(expected_mac, mac):
            raise InvalidMacError("MAC does not match")
        if sequence in self._seen_sequences[session_id]:
            raise SequenceAlreadySeenError("sequence has already been seen")
        self._seen_sequences[session_id].add(sequence)

    def _get_session(self, session_id: str) -> AuthenticatedSession:
        session = self._sessions.get(session_id)
        if session is None:
            raise UnknownSessionError("session is unknown")
        return session


def _validate_sequence(sequence: int) -> None:
    if isinstance(sequence, bool) or not isinstance(sequence, int) or sequence < 0:
        raise InvalidSequenceError("sequence must be a non-negative integer")
