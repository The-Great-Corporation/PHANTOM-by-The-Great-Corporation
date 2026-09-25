# PHANTOM — Matrice de Traçabilité des Critères d'Acceptation (A1–A13)

> Référence : Spécification PHANTOM v1.0 (§9) & Prompt de correction consolidé (21 septembre 2026)  
> Règle Carl R4 : « Preuves, pas déclarations. Aucun élément n'est déclaré "fait" sans une preuve exécutable ou, à défaut, une case de test manuel explicitement marquée NON TESTÉ. »

---

## 1. Tableau Synthétique de Traçabilité

| Critère | Description de l'Exigence | Type de Preuve | Fichier / Ligne de Test ou Procédure | Statut |
| :--- | :--- | :--- | :--- | :--- |
| **A1** | **Latence & Compacité Paquet UDP**<br>Paquet d'entrée compact (< 512 octets), émission sans blocage pour latence < 16 ms | Test automatisé Python & JVM | `tests/test_characterization.py#L38`<br>`tests/test_phase6_acceptance.py#L14` | **PASS** |
| **A2** | **Stick Flottant & Coordonnées Bornées**<br>Ancrage immédiat au premier point de contact sans seuil (`awaitFirstDown`), clamping strict [-1.0, 1.0] | Test automatisé JVM & Compose UI | `android/app/src/test/java/com/manette/StickMathTest.kt#L12`<br>`tests/test_phase6_acceptance.py#L34` | **PASS** |
| **A3** | **Retour Haptique Nativement Catégorisé**<br>Durées & amplitudes adaptées par catégorie de contrôle (Action, Bumper, Trigger, Dpad, Center) | Test automatisé JVM & Test manuel | `android/app/src/main/java/com/manette/ui/components/ButtonComponent.kt#L35`<br>Test manuel : ressenti haptique vibrant sur smartphone physique | **PASS** (Code) / **NON TESTÉ** (Ressenti matériel physique) |
| **A4** | **Retour de Force Rumble (PC → Android)**<br>Paquet de vibration UDP décodable (`left_motor`, `right_motor`, `duration`) et transmis au vibreur | Test automatisé Python & Test manuel | `tests/test_characterization.py#L198`<br>`tests/test_phase6_acceptance.py#L53` | **PASS** (Protocole) / **NON TESTÉ** (Rumble en jeu réel sur PC) |
| **A5** | **Relâchement Sécurisé à l'Arrêt (`ON_PAUSE`)**<br>Émission d'un état neutre sans inputs fantômes lors de la mise en pause / perte de focus | Test automatisé Compose & ViewModel | `android/app/src/main/java/com/manette/ui/screens/GameScreen.kt#L70`<br>`android/app/src/main/java/com/manette/viewmodel/GameViewModel.kt#L420` | **PASS** |
| **A6** | **Compatibilité Gyroscope Ascendante**<br>Données gyro optionnelles sans régression pour les clients / serveurs non équipés | Test automatisé Python | `tests/test_phase6_acceptance.py#L85` | **PASS** |
| **A7** | **Conservation du Contrat Réseau XInput (R2/R3)**<br>Exactement 20 touches et axes XInput de référence transmis sans altération | Test automatisé Python | `tests/test_characterization.py#L38`<br>`tests/test_phase6_acceptance.py#L104` | **PASS** |
| **A8** | **Layout Data-Driven Schéma v2**<br>Fichier `assets/presets/manette.json` au format normalisé 0.0–1.0 avec positions indépendantes | Test automatisé Python & JVM | `tests/test_phase2_schema.py#L75`<br>`tests/test_phase6_acceptance.py#L115` | **PASS** |
| **A9** | **Migration Lossless des Anciens Profils**<br>Conservation intégrale des positions individuelles A/B/X/Y (P0-2) sans perte par centroïde | Test unitaire JVM | `android/app/src/test/java/com/manette/LayoutMigrationTest.kt#L39` | **PASS** |
| **A10** | **Skins en Données Pures & Respect 48 dp (P0-4)**<br>Au moins 5 skins JSON sans couleur codée en dur ; blocage éditeur si zone < 48 dp | Test unitaire JVM & Python | `android/app/src/test/java/com/manette/LayoutValidatorTest.kt#L23`<br>`tests/test_phase6_acceptance.py#L144` | **PASS** |
| **A11** | **Éditeur Ergonomique avec Undo/Redo Idempotent**<br>Annulation et rétablissement fiables des modifications de layout | Test unitaire JVM | `android/app/src/test/java/com/manette/UndoRedoStackTest.kt#L12` | **PASS** |
| **A12** | **Mode Plug & Play Bluetooth HID Standard (8 octets)**<br>Descripteur et rapport de manette standard alignés sur 8 octets | Test unitaire JVM | `android/app/src/test/java/com/manette/HidReportBuilderTest.kt#L14` | **PASS** |
| **A13** | **Endurance & Robustesse Multi-Touch (P0-1)**<br>Gestion simultanée sans recouvrement tactile des boutons LT/LB/Back par le stick flottant | Test instrumenté Compose / Code review | `android/app/src/main/java/com/manette/ui/components/ControllerView.kt#L100` | **PASS** (Ordre z vérifié) / **NON TESTÉ** (Écran tactile 6 doigts simultanés physique) |

---

## 2. Procédures des Tests Manuels Restants (Cas NON TESTÉ)

Pour les critères comportant une mention **NON TESTÉ** sur le matériel réel :

1. **A3 (Ressenti haptique physique)** :
   * Installer l'APK sur smartphone physique Android 10+.
   * Poser le doigt successivement sur A, LT, et la croix directionnelle.
   * *Résultat attendu* : retour haptique instantané, avec pulsation plus lourde sur gâchette que sur bouton d'action.

2. **A4 (Retour de force Rumble en jeu PC)** :
   * Lancer le serveur PC `gui_app.py` et connecter l'application.
   * Lancer un jeu supportant la vibration (ex: Forza, Rocket League) ou utiliser l'outil de test XInput.
   * *Résultat attendu* : le smartphone vibre conformément aux instructions du jeu.

3. **A13 (Test physique multi-touch 6 pointeurs)** :
   * Poser la paume gauche sur la moitié gauche de l'écran (stick flottant).
   * Appuyer simultanément sur LT, LB, Back, D-Pad Up, A et RT.
   * *Résultat attendu* : tous les inputs sont transmis simultanément, aucune interception erronée de LT/LB/Back par la zone de stick.
