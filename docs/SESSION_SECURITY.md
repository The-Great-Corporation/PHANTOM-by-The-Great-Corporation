# Session security foundation

`server/core/session_security.py` fournit une fondation en mémoire pour la
phase sécurité/appairage. Elle est maintenant branchée au transport UDP Wi-Fi,
aux transports WebSocket et USB/ADB, ainsi qu'au serveur Bluetooth RFCOMM. Le
client Bluetooth RFCOMM reste volontairement refusé tant que son chemin
authentifié n'est pas implémenté.

## API et invariants

- `SessionSecurity.issue_pairing_token()` produit un token opaque aléatoire,
  avec une expiration monotone et une identité d'appareil associée.
- `consume_pairing_token()` accepte un token une seule fois. Un token expiré,
  inconnu ou déjà consommé est rejeté par une exception dédiée.
- La consommation crée une `AuthenticatedSession` avec un identifiant de
  session et une clé HMAC-SHA256 aléatoires. La clé reste en mémoire et n'est
  pas écrite sur disque.
- `compute_mac()` et `verify_mac()` couvrent une représentation JSON canonique
  (clés triées, séparateurs déterministes) du compteur et du message.
- `verify_mac()` compare les MAC avec `hmac.compare_digest()` et n'enregistre
  un compteur qu'après authentification réussie. Une session inconnue, un MAC
  invalide ou un compteur déjà vu est rejeté explicitement.

## Format UDP provisoire

La découverte reste publique et non privilégiée :
`{"type":"discover"}` retourne `discover_ack`, mais ne crée aucune session et
ne peut pas autoriser input, heartbeat ou ping.

Le client doit ensuite effectuer le handshake UDP provisoire en deux étapes.
Le `client_nonce` est généré côté client et le `token_id` peut être transporté
par une future UX QR :

```json
{
  "type": "pair_begin",
  "device_id": "phone-a",
  "token_id": "<identifiant public temporaire>",
  "client_nonce": "<nonce aléatoire>"
}
```

Le serveur vérifie le token et répond sans secret :

```json
{
  "type": "pair_challenge",
  "challenge_id": "<identifiant aléatoire>",
  "challenge": "<valeur aléatoire>"
}
```

Le challenge est conservé brièvement en mémoire et lié à l'adresse UDP,
`device_id`, `token_id` et `client_nonce`. Le client répond :

```json
{
  "type": "pair_proof",
  "device_id": "phone-a",
  "token_id": "<identifiant public temporaire>",
  "client_nonce": "<nonce aléatoire>",
  "challenge_id": "<identifiant aléatoire>",
  "challenge": "<valeur aléatoire>",
  "proof": "<HMAC-SHA256 du transcript>"
}
```

`proof` est `HMAC-SHA256(token_secret, canonical_json({type:
"pair_proof", token_id, device_id, client_nonce, challenge_id, challenge}))`.
Le serveur compare en temps constant, vérifie l'expiration, l'adresse et
l'usage unique du challenge, puis consomme atomiquement le challenge et le
token. Un datagramme historique `type: "pair"` est explicitement rejeté ;
aucun fallback n'est accepté.

La réponse `pair_ack` contient uniquement la confirmation, le `session_id` et
`server_challenge` (égal à `challenge`). Aucun secret de session n'est
transmis. Le client dérive localement la même clé avec :
`HMAC-SHA256(token_secret, "phantom-udp-session-v1\0" +
canonical_json({client_nonce, server_challenge, session_id}))`. La clé
résultante reste en mémoire et ne doit jamais être journalisée.

Chaque message de contrôle (`input`, `heartbeat` ou `ping`) doit contenir
`session_id`, `client_id`, `sequence` non négative, `data` (payload) et `mac`.
Le MAC HMAC-SHA256 couvre la forme canonique :
`{"type": ..., "client_id": ..., "session_id": ..., "payload": ...}` avec la
séquence ajoutée par `canonicalize_message`. Les messages inconnus, altérés,
non authentifiés ou rejoués sont rejetés avant `GamepadEmulator` et
`ConnectionManager`.

Le même enveloppe est obligatoire après handshake sur WebSocket, USB/ADB et
RFCOMM. La confirmation `connected` de ces flux fournit `session_id` et
`server_challenge`; les clients Android WebSocket et USB dérivent alors la clé
de session en mémoire et signent chaque `input` et `heartbeat`. Un message de
flux sans `session_id`, `sequence`, `data`/`payload` ou MAC valide est rejeté
avant tout changement d'état, et une séquence déjà authentifiée est refusée.
Le serveur RFCOMM applique ces contrôles même si aucun client Android RFCOMM
n'est actuellement disponible.

Ce format est provisoire : il n'utilise pas TLS. Il protège contre le rejeu
du handshake (challenge court, lié au contexte et consommé une seule fois),
mais un MITM actif reste hors périmètre de cette étape. Le client Android UDP exige désormais explicitement `device_id`, `token_id` et
`token_secret` injectés par l'appelant. Il refuse la connexion si l'un manque,
ne persiste aucun credential et ne journalise jamais de secret. Le nonce client
est généré par `SecureRandom`; la séquence et le MAC couvrent chaque input,
heartbeat et ping. La découverte reste disponible, mais ne constitue pas une
authentification.

La sécurité serveur + Android UDP n'est effective qu'avec ces credentials.

## Premier appairage Android

Le serveur produit un payload JSON temporaire avec `scheme:
"phantom-pairing"`, `version: 1`, `server`, `port`, `device_id`, `token_id`,
`token_secret` et `expires_at` (timestamp Unix). Le payload est à usage unique :
il doit être transmis au téléphone par QR ou par saisie explicite. L'application
refuse un schéma/version inconnus, un serveur ou port ambigu, un champ vide ou
un payload expiré. Le secret n'est jamais journalisé.

En attendant l'intégration du scanner caméra, l'écran Studio de configuration
propose une saisie JSON temporaire clairement identifiée. Enregistrer le
payload chiffre les credentials avec une clé Android Keystore et un fichier
privé à l'application ; ils ne sont pas placés dans SharedPreferences.

## Reconnexion et révocation

Après le premier appairage, la découverte UDP ne tente une reconnexion que si
les credentials Keystore sont encore valides. Le client n'affiche l'état
connecté qu'après réception et validation de `pair_ack`. Sans credentials
valides, l'interface conserve l'état « appairage requis » et n'utilise aucun
fallback non authentifié. Le bouton Révoquer efface le fichier chiffré et
déconnecte immédiatement le client ; un nouveau payload temporaire est alors
nécessaire.
