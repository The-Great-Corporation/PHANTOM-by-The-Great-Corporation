# Session security foundation

`server/core/session_security.py` fournit une fondation en mémoire pour la
phase sécurité/appairage et l'enrôlement mémorisé. Elle est branchée au transport UDP Wi-Fi,
aux transports WebSocket et USB/ADB, ainsi qu'au serveur Bluetooth RFCOMM. Le
client Bluetooth RFCOMM reste volontairement refusé tant que son chemin
authentifié n'est pas implémenté.

## API et invariants

- `SessionSecurity.issue_pairing_token()` produit un token opaque aléatoire,
  avec une expiration monotone et une identité d'appareil associée.
- `consume_pairing_token()` accepte un token une seule fois. Un token expiré,
  inconnu ou déjà consommé est rejeté par une exception dédiée.
- La consommation crée une `AuthenticatedSession` avec un identifiant de
  session et une clé HMAC-SHA256 aléatoires, et enregistre l'appareil dans le
  registre d'enrôlement mémorisé (`EnrolledDevice`). La clé reste en mémoire.
- `compute_mac()` et `verify_mac()` couvrent une représentation JSON canonique
  (clés triées, séparateurs déterministes) du compteur et du message.
- `verify_mac()` compare les MAC avec `hmac.compare_digest()` et n'enregistre
  un compteur qu'après authentification réussie. Une session inconnue, un MAC
  invalide ou un compteur déjà vu est rejeté explicitement.

## Premier appairage Android (QR / Token)

Le serveur produit un payload JSON temporaire avec `scheme:
"phantom-pairing"`, `version: 1`, `server`, `port`, `device_id`, `token_id`,
`token_secret` et `expires_at` (timestamp Unix). Le payload QR est à usage unique :
il doit être transmis au téléphone par QR ou par saisie explicite. L'application
refuse un schéma/version inconnus, un serveur ou port ambigu, un champ vide ou
un payload expiré pour le premier scan. Le secret n'est jamais journalisé.

Le premier appairage s'effectue via le handshake `pair_begin` / `pair_challenge` / `pair_proof` -> `pair_ack` (UDP) ou `connect` / `pair_challenge` / `pair_proof` -> `connected` (WebSocket/USB/RFCOMM).

Enregistrer le payload chiffre les credentials (`deviceId`, `tokenSecret`, etc.) avec une clé Android Keystore et un fichier privé à l'application.

## Enrôlement mémorisé et reconnexion transparente

Après un premier appairage réussi, le serveur conserve l'appareil dans son registre d'enrôlement :
- `device_id`
- `enrollment_secret` (`token_secret`)
- `registered_at`
- `last_activity`
- `is_revoked`

L'expiration est glissante (`remembered_device_ttl_seconds`, par défaut 30 jours = 2 592 000s) :
- Chaque reconnexion authentifiée réussie repousse la date d'expiration en mettant à jour `last_activity = now`.
- Une tentative échouée ne prolonge jamais la validité.
- Un appareil expiré (inactif depuis plus de 30 jours) ou révoqué doit refaire un appairage QR.

### Protocole de reconnexion (sans re-scan QR)

1. **Reconnection Begin** (Client -> Serveur) :
   ```json
   {
     "type": "reconnect_begin",
     "device_id": "phone-a",
     "client_nonce": "<nonce aléatoire>"
   }
   ```
2. **Reconnection Challenge** (Serveur -> Client) :
   ```json
   {
     "type": "reconnect_challenge",
     "challenge_id": "<challenge_id>",
     "challenge": "<challenge_secret>"
   }
   ```
3. **Reconnection Proof** (Client -> Serveur) :
   ```json
   {
     "type": "reconnect_proof",
     "device_id": "phone-a",
     "client_nonce": "<nonce>",
     "challenge_id": "<challenge_id>",
     "challenge": "<challenge_secret>",
     "proof": "<HMAC-SHA256(enrollment_secret, transcript)>"
   }
   ```
4. **Connected Response** (Serveur -> Client) :
   ```json
   {
     "type": "connected",
     "device_id": "phone-a",
     "session_id": "<nouvelle_session_id>",
     "server_challenge": "<challenge_secret>"
   }
   ```

Chaque reconnexion génère un **nouveau `session_id`**, une **nouvelle clé de session dérivée** et **réinitialise la séquence à 0**.
Les messages de contrôle ultérieurs (`input`, `heartbeat`, `ping`) restent protégés par le MAC HMAC-SHA256 habituel avec la nouvelle clé de session.

## Révocation

Le bouton « Révoquer » (ou l'appel `revoke_device(device_id)` côté serveur) efface les credentials locaux chiffrés et invalide immédiatement l'enrôlement côté serveur. Une tentative de reconnexion sur un appareil révoqué est immédiatement rejetée avec `EnrolledDeviceRevokedError`, et un nouvel appairage QR est requis.
