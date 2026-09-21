# Audit Phase 0 — État Initial & Diagnostic du Codebase PHANTOM

> Date de réalisation : Septembre 2026  
> Auteur : Antigravity (Google DeepMind) pour Carl (The Great Corporation)  
> Référence : Spécification v1.0 & Prompt de correction consolidé

---

## 1. Contexte et Objectifs de l'Audit

L'audit initial (Phase 0) a été mené afin d'évaluer la robustesse du système existant avant toute intervention sur l'expérience de jeu.
Pour rappel, les modules de communication réseau et l'émulation matérielle PC (ViGEmBus) ont été validés par Carl et constituent le socle immuable (Règles R1, R2, R3).
L'audit s'est concentré sur :
1. L'application mobile Android (Jetpack Compose, MVVM).
2. Le moteur d'entrée tactile et la gestion des contrôles virtuels.
3. Le modèle de persistance et de configuration des profils.
4. L'implémentation du mode autonome Bluetooth HID (Plug & Play).

---

## 2. Synthèse des Anomalies et Diagnostics Initiaux

### 2.1 Bug Bloquant : Recouvrement Tactile LT / LB / Back (P0-1)
* **Symptôme** : Impossibilité d'actionner les gâchettes gauches ou le bouton Back lorsque le stick flottant est actif.
* **Origine dans le code** : Dans `ControllerView.kt`, le composable `FloatingJoystickZone` (occupant 50 % de l'écran en largeur et 100 % de la hauteur) était composé **après** `btn_lt`, `btn_lb` et `btn_back`. Dans le moteur Jetpack Compose, l'ordre z place le dernier élément enfant au sommet de la pile d'événements tactiles, interceptant ainsi tous les touchers sur la moitié gauche.
* **Résolution** : Composition de `FloatingJoystickZone` en première position (fond) pour que les boutons situés dessus captent prioritairement leurs gestes.

### 2.2 Régression Critique : Perte des Positions ABXY lors de la Migration (P0-2)
* **Symptôme** : Les profils personnalisés de l'ancienne version voyaient leurs boutons A, B, X, Y fusionnés en un seul groupe au centroïde, écrasant les réglages individuels.
* **Origine dans le code** : `ProfileManager.migrateIfNeeded()` calculait la moyenne des coordonnées de `btn_a`, `btn_b`, `btn_x`, `btn_y` pour insérer une clé unique `abxy`, tout en supprimant les clés d'origine (`positions.remove(k)`).
* **Résolution** : Extraction de `MigrationLogic.kt` en Kotlin pur. Préservation stricte des 4 clés individuelles, avec calcul de l'alias de groupe `abxy` sans suppression des enfants.

### 2.3 Réactivité Tactile : Seuil de Glissement sur Stick Flottant (P0-3)
* **Symptôme** : Le stick flottant ne s'ancrait pas immédiatement à l'endroit où le pouce se posait ; le premier mouvement semblait "sauter" ou être ignoré.
* **Origine dans le code** : Utilisation de `detectDragGestures` dans Compose, qui attend le franchissement du seuil système `touchSlop` (8 à 16 dp) avant de déclencher `onDragStart`.
* **Résolution** : Remplacement par une boucle gestuelle directe `awaitEachGesture` avec `awaitFirstDown(requireUnconsumed = false)`. Ancrage instantané au contact sans aucun seuil.

### 2.4 Ergonomie & Accessibilité : Zones Tactiles Hors Normes (P0-4)
* **Symptôme** : Possibilité de réduire des boutons jusqu'à des dimensions de ~25 dp via l'éditeur, rendant la manette injouable et non conforme aux standards Google/Material.
* **Origine dans le code** : Absence de validation bloquante dans l'éditeur et slider autorisant un scale de 0.60x sur des contrôles de base 42 dp.
* **Résolution** : Création du module `LayoutValidator.kt` imposant la limite stricte de 48 dp réels. Blocage immédiat de la sauvegarde et dialogue d'explication si un contrôle est trop petit.

### 2.5 Dualité et Code Mort Bluetooth HID (P2-8)
* **Symptôme** : Incohérence entre `HidDeviceProfile.kt` (générant un paquet de 6 octets) et le descripteur réel de `BluetoothHidService.kt` (attendant 8 octets).
* **Origine dans le code** : Deux implémentations concurrentes non synchronisées.
* **Résolution** : Extraction d'un constructeur unique de rapport 8 octets `HidReportBuilder.kt` partagé et testé en JVM.

---

## 3. Stratégie de Test et Règles de Non-Régression

Conformément à la règle Carl R4 (« Preuves, pas déclarations »), toute nouvelle fonctionnalité ou correction s'appuie désormais sur :
1. Des modules métier en Kotlin pur découplés d'Android (`StickMath`, `MigrationLogic`, `LayoutValidator`, `HidReportBuilder`, `UndoRedoStack`), testables unitairement en environnement JVM sans émulateur.
2. Une suite d'intégration Python validant l'intégrité du protocole UDP, des structures de paquets XInput et du descripteur binaire HID.
3. Une traçabilité systématique consignée dans `docs/TRACABILITE_CRITERES.md`.
