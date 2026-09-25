# PHANTOM by The Great Corporation — Guide d'Utilisation Officiel

Ce guide présente en détail l'ergonomie, les fonctionnalités avancées et les réglages de précision de **PHANTOM by The Great Corporation**.

---

## Sommaire

1. [Architecture de l'Application (3 Écrans)](#1-architecture-de-lapplication-3-écrans)
2. [Modes de Contrôle](#2-modes-de-contrôle)
3. [Moteur Tactile & Maîtrise des Sticks](#3-moteur-tactile--maîtrise-des-sticks)
4. [Le Studio de Configuration Haute Couture](#4-le-studio-de-configuration-haute-couture)
5. [Le Volet Escamotable en Jeu (Quick Settings Drawer)](#5-le-volet-escamotable-en-jeu-quick-settings-drawer)
6. [Gestion des Profils & Personnalisations](#6-gestion-des-profils--personnalisations)
7. [Mode Hybride Clavier / Souris](#7-mode-hybride-clavier--souris)

---

## 1. Architecture de l'Application (3 Écrans)

L'expérience PHANTOM repose sur une navigation fluide articulée autour de 3 écrans immersifs :

* **Écran 1 — Accueil & Prestige** :  
  L'entrée en matière signée TGC. Reconnaissance automatique de l'IP du serveur PC sur votre Wi-Fi, choix instantané du mode (*The Great* ou *Plug & Play*), et lancement immédiat avec **▶ JOUER**.
* **Écran 2 — Studio de Configuration & Calibration** :  
  L'atelier de précision où chaque joueur ajuste son matériel virtuel : sélection de thèmes visuels, import d'arrière-plans ou GIFs animés, étalonnage des zones mortes et sensibilité des axes.
* **Écran 3 — Manette Immersive Plein Écran** :  
  100 % dédié à votre session de jeu. Les barres système Android sont masquées, l'affichage tourne en 120/240 Hz pour une réactivité maximale, avec une pastille discrète de télémétrie (latence en millisecondes et autonomie batterie).

---

## 2. Modes de Contrôle

### Mode « The Great » (Hautes Performances PC)
Conçu pour les joueurs PC exigeants sur Steam, Xbox App, Epic Games Store ou émulateurs :
* Émulation matérielle authentique d'une manette **Xbox 360** ou **PlayStation DualShock 4** au niveau du noyau Windows.
* Streaming UDP binaire ultraléger (44 octets par frame) garantissant une latence sub-milliseconde.
* Sécurisation par jetons cryptographiques HMAC éliminant toute interférence externe.

### Mode « Plug & Play » (Bluetooth HID Universel)
Idéal en mobilité, en déplacement ou pour jouer sur TV connectée :
* Ne nécessite aucun logiciel ou serveur récepteur.
* Le smartphone est détecté comme un contrôleur Bluetooth physique natif par Windows, macOS, Android TV, Apple TV ou consoles compatibles.

---

## 3. Moteur Tactile & Maîtrise des Sticks

L'un des accomplissements majeurs de PHANTOM réside dans son moteur tactile biomécanique :

### A. Isolation Multi-Touch Stricte
Sur les contrôleurs virtuels ordinaires, appuyer sur une touche d'action (A, B, X, Y) ou une gâchette (LT, RT) entraîne des micro-saccades ou le décrochage du stick directionnel gauche.  
Dans PHANTOM, chaque doigt possède son propre **canal de suivi matériel indépendant** (`pointerId`). Vous pouvez exécuter des combos rapides à droite sans aucune interférence sur la fluidité de votre course à gauche.

### B. Stick Flottant à Ancre Suiveuse (*Following Anchor*)
En mode flottant, le stick s'ancre précisément là où votre pouce entre en contact avec l'écran. Si votre doigt glisse au-delà du rayon d'action maximal, le point d'origine du stick suit doucement votre pouce. Vous conservez ainsi une course de braquage maximale sans jamais perdre le contact.

### C. Commutateur Mode Flottant / Mode Fixe
Si vous préférez des repères spatiaux fixes et immuables :
* Basculez en **Mode Fixe** en un clic.
* Les sticks restent ancrés à des coordonnées exactes sur l'écran, pour les joueurs habitués aux bornes d'arcade ou aux repères physiques précis.

---

## 4. Le Studio de Configuration Haute Couture

Accédez au Studio de Configuration depuis l'écran d'accueil pour façonner votre contrôleur :

### Packs de Thèmes (Skins)
* **Xbox Series Edition** : Disposition asymétrique, boutons colorés iconiques A, B, X, Y et gâchettes analogiques progressives.
* **PlayStation DualSense Edition** : Disposition symétrique des sticks, symboles légendaires Carré, Triangle, Rond, Croix.
* **Nintendo Switch Pro Edition** : Inversion AB/XY pour les habitués des titres Nintendo.
* **Cyberpunk Neon Edition** : Contours luminescents haute intensité pour sessions de jeu nocturnes.
* **Ghost Minimalist Edition** : Éléments semi-transparents ultrafins offrant une visibilité totale sur l'arrière-plan.

### Arrière-plans & GIFs Animés Personnalisés
* Touchez le bouton d'importation pour choisir n'importe quelle photo ou GIF animé dans votre galerie Android.
* **Curseur d'Assombrissement Immersion** : Réglez l'opacité du calque sombre supérieur (0 % à 90 %) pour équilibrer la beauté visuelle de votre GIF et la visibilité parfaite des touches.

### Calibration des Contrôles
* **Sensibilité des Joysticks** : Réglage fin du multiplicateur d'amplitude (de 0.5x à 2.0x).
* **Zone Morte (Deadzone)** : Suppression du « stick drift » involontaire grâce à un seuil réglable au millimètre près.

---

## 5. Le Volet Escamotable en Jeu (Quick Settings Drawer)

Pendant une partie, effectuez un léger glissement depuis la bordure gauche ou touchez l'icône discrète en bord d'écran pour déployer le tiroir rapide :
* Basculez instantanément entre **Stick Gauche Flottant** et **Stick Gauche Fixe**.
* Réajustez la sensibilité des contrôles si un jeu nécessite une visée plus douce ou plus incisive.
* Visualisez la latence réseau en direct.
* Relancez une connexion immédiate sans quitter la partie.

---

## 6. Gestion des Profils & Personnalisations

* **Profils Multi-Jeux** : Sauvegardez des configurations dédiées selon vos genres de prédilection (FPS, Course automobile, Aventure RPG, Combat).
* **Partage & Exportation** : Exportez vos profils au format JSON ou via QR Code pour transférer vos réglages sur un autre appareil ou les partager avec d'autres joueurs.

---

## 7. Mode Hybride Clavier / Souris

Pour les jeux PC anciens ou les productions indépendantes ne gérant pas nativement les manettes :
* Le serveur PHANTOM traduit les mouvements de stick en déplacements fluides du curseur souris.
* Les touches virtuelles peuvent être réassignées aux raccourcis clavier de votre choix (touches ZQSD, Espace, Échap, Clics souris).

---
*The Great Corporation™ — Documentation Officielle PHANTOM — Tous droits réservés.*
