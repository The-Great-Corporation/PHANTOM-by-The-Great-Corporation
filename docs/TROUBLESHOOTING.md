# PHANTOM by The Great Corporation — Guide de Dépannage Officiel

Ce guide recense les diagnostics et solutions pour résoudre rapidement tout incident technique sur **PHANTOM by The Great Corporation**.

---

## Sommaire

1. [Problèmes liés au Serveur PC (Windows)](#1-problèmes-liés-au-serveur-pc-windows)
2. [Problèmes de Pilote & Émulation ViGEmBus](#2-problèmes-de-pilote--émulation-vigembus)
3. [Problèmes de Connexion & Réseau](#3-problèmes-de-connexion--réseau)
4. [Problèmes sur l'Application Mobile Android](#4-problèmes-sur-lapplication-mobile-android)
5. [Diagnostics Avancés & Outils TGC](#5-diagnostics-avancés--outils-tgc)

---

## 1. Problèmes liés au Serveur PC (Windows)

### A. Message : « Une autre instance de Phantom Server est déjà active »
* **Cause** : Le mécanisme de protection TGC via Mutex système (`Global\PhantomServerTGC_SingleInstance_Mutex`) a détecté un processus serveur Phantom déjà en cours d'exécution.
* **Solution** :
  1. Vérifiez dans votre barre des tâches si une fenêtre Phantom Server est minimisée.
  2. Si le processus est orphelin, ouvrez le Gestionnaire des tâches (`Ctrl + Maj + Échap`), terminez la tâche `python.exe` associée à Phantom, puis relancez le lanceur.

### B. Le serveur ne démarre pas ou la fenêtre se ferme immédiatement
* **Cause** : Dépendances manquantes ou environnement Python non détecté.
* **Solution** :
  1. Lancez le script d'installation assisté :
     ```powershell
     .\install.ps1
     ```
  2. Le script reconstruira automatiquement l'environnement virtuel `.venv` et installera toutes les dépendances requises.

---

## 2. Problèmes de Pilote & Émulation ViGEmBus

### A. Les jeux PC ne détectent aucune manette
* **Symptôme** : Le serveur indique que la connexion avec le smartphone est établie, mais Steam ou vos jeux ne réagissent pas.
* **Cause** : Le pilote `ViGEmBus` n'est pas installé ou son service est arrêté.
* **Solution** :
  1. Vérifiez l'installation de ViGEmBus via PowerShell :
     ```powershell
     Get-Service -Name "ViGEmBus" -ErrorAction SilentlyContinue
     ```
  2. Si le service n'est pas présent, téléchargez et installez la dernière version officielle : [ViGEmBus GitHub Releases](https://github.com/ViGEm/ViGEmBus/releases).
  3. Redémarrez votre PC après l'installation pour initialiser le pilote de bus au niveau du noyau.

---

## 3. Problèmes de Connexion & Réseau

### A. L'Auto-Discovery Wi-Fi ne trouve pas le PC
* **Symptômes** : Le voyant réseau de l'application mobile reste rouge ou en recherche continue.
* **Vérifications** :
  1. **Même sous-réseau** : Assurez-vous que le PC et le smartphone sont connectés à la même bande Wi-Fi (ex: pas l'un sur le Wi-Fi « Invité » et l'autre sur le réseau principal).
  2. **Isolation AP (Client Isolation)** : Certaines box désactivent la communication directe entre appareils Wi-Fi. Désactivez l'option « Isolation des clients » dans les réglages de votre routeur.
  3. **Pare-feu Windows** : Autorisez Python à communiquer sur les réseaux privés. Pour ouvrir manuellement les ports :
     ```powershell
     New-NetFirewallRule -DisplayName "PHANTOM Server UDP" -Direction Inbound -LocalPort 8888 -Protocol UDP -Action Allow
     New-NetFirewallRule -DisplayName "PHANTOM Server WS" -Direction Inbound -LocalPort 8889 -Protocol TCP -Action Allow
     ```
  4. **Saisie manuelle** : Si l'auto-découverte est bloquée par votre routeur, déroulez le panneau manuel sur le mobile et saisissez l'adresse IPv4 affichée sur le dashboard du serveur PC.

### B. Mode Câble USB (ADB) non reconnu
* **Symptômes** : La connexion USB ne s'établit pas.
* **Vérifications** :
  1. Vérifiez que le smartphone est reconnu par ADB :
     ```bash
     adb devices
     ```
  2. Si l'état affiche `unauthorized`, déverrouillez votre écran mobile et cochez *« Toujours autoriser depuis cet ordinateur »*.
  3. Relancez la redirection de port :
     ```bash
     adb reverse tcp:8890 tcp:8890
     ```

---

## 4. Problèmes sur l'Application Mobile Android

### A. Conflit avec la navigation gestuelle Android
* **Symptôme** : En tentant d'ouvrir le tiroir d'options rapides en jeu (*Quick Settings Drawer*), le système Android déclenche le retour en arrière de l'OS.
* **Solution** :
  * L'application mobile PHANTOM active le mode plein écran immersif total (`immersive-sticky`) pour neutraliser les gestes système en cours de jeu.
  * Si vous utilisez la navigation gestuelle Android, faites glisser le doigt depuis l'encoche dédiée sur la bordure gauche pour ouvrir le panneau sans interférer avec l'OS.

### B. Latence ressentie ou micro-saccades en Wi-Fi
* **Causes fréquentes** :
  1. Smartphone connecté sur la bande 2,4 GHz saturée. **Privilégiez toujours la bande 5 GHz ou Wi-Fi 6**.
  2. Mode d'économie d'énergie agressif sur le smartphone. Désactivez l'optimisation de batterie pour l'application PHANTOM afin d'empêcher Android de ralentir le processeur ou la carte Wi-Fi.

---

## 5. Diagnostics Avancés & Outils TGC

Pour obtenir un rapport complet sur l'état de votre machine et identifier tout problème :
1. Lancez le scanner de paysage officiel en ligne de commande :
   ```powershell
   .\.venv\Scripts\python.exe server\core\landscape_scanner.py
   ```
2. Le scanner inspectera en temps réel :
   - La présence d'instances concurrentes.
   - La santé du pilote ViGEmBus.
   - La disponibilité de chaque port d'écoute (`8888`, `8889`, `8890`, `8887`).
   - L'état du pont ADB.

Si un problème persiste, contactez le support officiel : `support@thegreatcorporation.com`.

---
*The Great Corporation™ — Documentation Officielle PHANTOM — Tous droits réservés.*
