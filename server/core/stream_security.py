"""Authenticated handshake helpers shared by stream transports."""

from typing import Any, Mapping

from .session_security import SessionSecurity, AuthenticatedSession, SessionSecurityError


class StreamAuthenticator:
    """Run the challenge/proof handshake on a connected stream."""

    def __init__(self, security: SessionSecurity):
        self.security = security

    def begin(self, message: Mapping[str, Any], address: tuple) -> dict:
        device_id = message.get("device_id") or message.get("client_id")
        token_id = message.get("token_id")
        client_nonce = message.get("client_nonce")
        challenge = self.security.create_pairing_challenge(
            token_id, device_id, client_nonce, address
        )
        return {
            "type": "pair_challenge",
            "challenge_id": challenge.challenge_id,
            "challenge": challenge.challenge,
        }

    def complete(
        self, message: Mapping[str, Any], address: tuple
    ) -> AuthenticatedSession:
        device_id = message.get("device_id") or message.get("client_id")
        return self.security.consume_pairing_challenge(
            message.get("challenge_id"),
            message.get("challenge"),
            message.get("token_id"),
            device_id,
            message.get("client_nonce"),
            message.get("proof"),
            address,
        )

    def verify_message(
        self, session: AuthenticatedSession, message: Mapping[str, Any]
    ) -> Any:
        """Verify the authenticated envelope shared by stream transports."""
        session_id = message.get("session_id")
        if session_id != session.identity.session_id:
            raise SessionSecurityError("session_id does not match authenticated session")
        client_id = message.get("client_id") or message.get("device_id")
        if client_id != session.identity.device_id:
            raise SessionSecurityError("client_id does not match authenticated session")
        sequence = message.get("sequence")
        mac_value = message.get("mac")
        if not isinstance(mac_value, str) or len(mac_value) != 64:
            raise SessionSecurityError("stream message requires a MAC")
        try:
            mac = bytes.fromhex(mac_value)
        except ValueError as exc:
            raise SessionSecurityError("stream message MAC is malformed") from exc
        if "data" in message:
            payload = message["data"]
        elif "payload" in message:
            payload = message["payload"]
        else:
            raise SessionSecurityError("stream message requires data or payload")
        authenticated_message = {
            "client_id": client_id,
            "session_id": session_id,
            "payload": payload,
        }
        self.security.verify_mac(session_id, sequence, authenticated_message, mac)
        return payload
