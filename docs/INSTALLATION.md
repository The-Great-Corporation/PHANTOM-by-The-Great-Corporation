# PHANTOM by The Great Corporation — Guide d'Installation Officiel

Ce guide détaille l'installation, la configuration et le déploiement de **PHANTOM by The Great Corporation**, système de contrôleur virtuel hybride pour PC Windows et smartphones Android.

---

## Sommaire

1. [Prérequis Matériels & Logiciels](#1-prérequis-matériels--logiciels)
2. [Installation Serveur PC Windows (Zéro-Friction)](#2-installation-serveur-pc-windows-zéro-friction)
3. [Installation Alternative / Manuelle (Mode Développeur)](#3-installation-alternative--manuelle-mode-développeur)
4. [Installation du Client Mobile Android](#4-installation-du-client-mobile-android)
5. [Modes de Connexion & Appairage](#5-modes-de-connexion--appairage)
6. [Contrôle Post-Installation](#6-contrôle-post-installation)

---

## 1. Prérequis Matériels & Logiciels

### Pour le Serveur PC (Windows)
* **Système d'exploitation** : Windows 10 ou Windows 11 (64-bit).
* **Python** : Version 3.10 à 3.12 (installée avec l'option « Add Python to PATH » cochée).
* **Pilote Virtuel** : [ViGEmBus](https://github.com/ViGEm/ViGEmBus/releases) version 1.22+ (requis pour l'émulation matérielle Xbox 360 / DualShock 4).
* **Réseau** : Carte Wi-Fi ou Ethernet connectée au même réseau local que le smartphone.

### Pour l'Application Mobile (Android)
* **Système d'exploitation** : Android 8.0 (Oreo / API 26) ou supérieur (Android 11+ recommandé).
* **Connectivité** : Wi-Fi (Mode The Great), Câble USB avec Débogage USB (Mode Zéro-Latence ADB), ou Bluetooth 4.2+ (Mode Plug & Play).

---

## 2. Installation Serveur PC Windows (Zéro-Friction)

The Great Corporation a conçu un installateur pré-vol automatisé avec **scanner de paysage** pour éliminer tout risque de conflit de ports ou de doublons.

### Étape 1 : Exécution du script d'installation assisté
Ouvrez PowerShell dans le dossier racine du projet et exécutez :
```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\install.ps1
```

Le script exécute automatiquement les 5 étapes du diagnostic pré-vol :
1. **Création & Validation de l'environnement virtuel dédié** (`.venv`).
2. **Installation certifiée des dépendances** (`vgamepad`, `websockets`, `pyyaml`, `pyserial`).
3. **Scan de paysage TGC** :
   - Vérification de l'unicité de processus (Mutex système `Global\PhantomServerTGC_SingleInstance_Mutex`).
   - Détection du pilote `ViGEmBus` dans le registre Windows.
   - Analyse d'écoute des ports `8888` (UDP), `8889` (WebSocket), `8890` (USB ADB) et `8887` (Bluetooth).
   - Détection automatique de l'adresse IP locale.
4. **Génération du Raccourci Bureau** « PHANTOM Server TGC » pour un accès immédiat.

### Étape 2 : Démarrage du Serveur en 1 Clic
* Double-cliquez sur le raccourci créé sur votre Bureau, **ou**
* Lancez le fichier [`Lancer_Serveur_Phantom_TGC.bat`](../Lancer_Serveur_Phantom_TGC.bat).
* L'interface graphique officielle s'ouvre : cliquez sur **« DÉMARRER LE SERVEUR PHANTOM »**.

---

## 3. Installation Alternative / Manuelle (Mode Développeur)

Si vous préférez configurer l'environnement manuellement :

1. **Créer l'environnement virtuel** :
   ```powershell
   python -m venv .venv
   .\.venv\Scripts\Activate.ps1
   ```

2. **Installer les dépendances** :
   ```powershell
   pip install --upgrade pip
   pip install -r server/requirements.txt
   ```

3. **Installer manuellement ViGEmBus** :
   * Téléchargez `ViGEmBusSetup_x64.msi` depuis les [releases officielles ViGEm](https://github.com/ViGEm/ViGEmBus/releases).
   * Exécutez l'installateur et redémarrez votre PC si demandé.

4. **Lancer le serveur en console ou interface graphique** :
   * Console : `python server/main.py`
   * Dashboard graphique : `python server/gui_app.py`

---

## 4. Installation du Client Mobile Android

### Méthode A : Installation directe de l'APK (Pré-Production / Release)
1. Transférez le fichier `PHANTOM-TGC-Release.apk` sur votre smartphone Android.
2. Activez l'option *« Installer des applications inconnues »* dans les paramètres de votre navigateur ou gestionnaire de fichiers.
3. Touchez le fichier APK pour finaliser l'installation.

### Méthode B : Compilation via Android Studio
1. Lancez **Android Studio**.
2. Ouvrez le dossier `android/`.
3. Laissez Gradle synchroniser les dépendances (Jetpack Compose, Kotlin 1.9+).
4. Branchez votre smartphone en mode Débogage USB et cliquez sur **Run 'app'** (`Shift + F10`).

---

## 5. Modes de Connexion & Appairage

### A. Wi-Fi Local (Auto-Discovery Zéro-Friction — Recommandé)
1. Assurez-vous que le PC et le smartphone sont connectés à la même box ou point d'accès Wi-Fi.
2. Lancez le serveur Windows via `Lancer_Serveur_Phantom_TGC.bat`.
3. Ouvrez l'application PHANTOM sur votre mobile :
   - Le serveur TGC est automatiquement scanné et localisé sur le réseau local.
   - Le voyant passe au vert avec mention de l'IP du serveur.
4. Cliquez sur **▶ JOUER** : la session démarre immédiatement.

### B. Câble USB (Zéro-Latence absolue via ADB Reverse)
Pour les tournois compétitifs et les jeux réclamant 0 ms de délai réseau :
1. Activez le **Débogage USB** sur votre smartphone (Paramètres → Options de développement).
2. Branchez le smartphone au PC avec un câble USB data.
3. Exécutez la redirection de port :
   ```bash
   adb reverse tcp:8890 tcp:8890
   ```
4. Dans l'application mobile, basculez le mode de transport sur **USB**.

### C. Mode « Plug & Play » (Bluetooth HID Universel)
Sans aucun serveur ni logiciel installé sur la machine réceptrice :
1. Sur l'écran d'accueil de l'application mobile, choisissez le mode **« PLUG & PLAY »**.
2. Activez le Bluetooth sur votre récepteur (PC, Mac, Smart TV, console).
3. Associez l'appareil détecté sous le nom **« Phantom »**.
4. Le smartphone est immédiatement reconnu comme une manette Bluetooth standard.

---

## 6. Contrôle Post-Installation

Pour valider l'intégrité de votre installation :
* Exécutez la suite complète de tests de conformité serveur :
  ```powershell
  .\.venv\Scripts\python.exe -m pytest server/tests
  ```
  *(Résultat attendu : 54/54 tests passés).*
* Ouvrez le Panneau de Configuration Windows → *« Périphériques et imprimantes »* : lorsque le serveur est démarré, une manette virtuelle Xbox 360 ou DualShock apparaît dynamiquement dans la liste.

---
*The Great Corporation™ — Documentation Officielle PHANTOM — Tous droits réservés.*
