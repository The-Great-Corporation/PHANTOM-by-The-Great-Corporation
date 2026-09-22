# Manette - Installation Guide

Complete installation guide for the Virtual Gamepad System with two modes: "The Great" (PC server-based) and "Plug & Play" (native Bluetooth HID).

## Table of Contents

1. [System Requirements](#system-requirements)
2. [Python Server Installation](#python-server-installation)
3. [Android Client Installation](#android-client-installation)
4. [Connection Setup](#connection-setup)
5. [Troubleshooting](#troubleshooting)

---

## System Requirements

### For "The Great" Mode (PC Server)

**Windows PC:**
- Windows 10 or higher
- Python 3.10 or higher
- ViGEm Bus driver (for gamepad emulation)
- Network adapter (Wi-Fi or Ethernet)

**Android Device:**
- Android 7.0 (Nougat) or higher
- Wi-Fi, Bluetooth, or USB connection

### For "Plug & Play" Mode (Bluetooth HID)

**Android Device:**
- Android 9.0 (Pie) or higher
- Bluetooth 4.0 or higher
- Bluetooth HID profile support

**Host Device:**
- Any device with Bluetooth support (PC, TV, console, etc.)

---

## Python Server Installation

### Step 1: Install Python

1. Download Python 3.10+ from [python.org](https://www.python.org/downloads/)
2. Run the installer and check **"Add Python to PATH"**
3. Verify installation:
   ```bash
   python --version
   ```

### Step 2: Install ViGEm Bus Driver

The ViGEm Bus driver is required for gamepad emulation on Windows.

1. Download ViGEm Bus from [GitHub Releases](https://github.com/ViGEm/ViGEmBus/releases)
2. Extract the downloaded archive
3. Run `ViGEmBusDriverSetup.exe` as Administrator
4. Follow the installation wizard
5. Restart your computer

### Step 3: Install Python Dependencies

1. Navigate to the server directory:
   ```bash
   cd server
   ```

2. Install required packages:
   ```bash
   pip install -r requirements.txt
   ```

   This will install:
   - `vgamepad` - Xbox 360 gamepad emulation
   - `websockets` - WebSocket server
   - `pyserial` - Serial communication

### Step 4: Configure the Server

Edit `server/config/server_config.json` to customize settings:

```json
{
  "server": {
    "host": "0.0.0.0",
    "udp_port": 8888,
    "websocket_port": 8889,
    "bluetooth_port": 8887,
    "usb_port": 8890
  },
  "gamepad": {
    "emulation_type": "xbox360",
    "vibration_enabled": true,
    "deadzone_left": 0.1,
    "deadzone_right": 0.1
  },
  "connection": {
    "max_clients": 4,
    "reconnect_interval": 5,
    "timeout": 30
  }
}
```

### Step 5: Start the Server

Run the server:
```bash
python main.py
```

You should see:
```
INFO - Starting Virtual Gamepad Server...
INFO - Gamepad emulator initialized successfully
INFO - Starting UDP server on 0.0.0.0:8888
INFO - UDP server listening on 0.0.0.0:8888
INFO - Starting WebSocket server on 0.0.0.0:8889
INFO - WebSocket server listening on 0.0.0.0:8889
```

---

## Android Client Installation

### Option 1: Build from Source (Android Studio)

1. **Install Android Studio**
   - Download from [developer.android.com/studio](https://developer.android.com/studio)
   - Install with default settings
   - Install Android SDK (API 26+)

2. **Open the Project**
   - Launch Android Studio
   - File → Open → Navigate to `android/` directory
   - Wait for Gradle sync to complete

3. **Build the APK**
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Locate the APK in `android/app/build/outputs/apk/debug/`

4. **Install on Device**
   - Enable USB debugging on your Android device
   - Connect via USB
   - Run: `adb install app-debug.apk`
   - Or transfer APK and install manually

### Option 2: Install Pre-built APK (if available)

1. Download the APK from the releases page
2. Enable "Install from unknown sources" in Android settings
3. Open the APK file to install

### Grant Permissions

On first launch, grant the following permissions:

- **Internet** - For network connections
- **Bluetooth** - For Bluetooth connections
- **Nearby Devices** - For Bluetooth scanning (Android 12+)
- **Vibration** - For haptic feedback
- **Body Sensors** - For gyroscope/accelerometer

---

## Connection Setup

### Wi-Fi Connection (Recommended)

1. **Ensure both devices are on the same network**
   - Connect PC and Android to the same Wi-Fi router

2. **Find your PC's IP address**
   - Windows: Open Command Prompt, run `ipconfig`
   - Look for "IPv4 Address" (e.g., 192.168.1.100)

3. **Configure the Android app**
   - Open the app
   - Select "The Great" mode

### Authenticated UDP pairing

UDP requires credentials supplied explicitly by the application:
`device_id`, `token_id`, and `token_secret`. The server and Android UDP client
are secure only when all three values come from a trusted provisioning flow.
Do not put the secret in source control, logs, preferences, or other
persistent storage. The Android app now accepts the versioned temporary JSON
payload described below and stores it encrypted with Android Keystore.
   - Go to Configuration
   - Enter your PC's IP address
   - Select "UDP" as connection type
   - Port: 8888 (default)

4. **Connect**
   - Tap "Start Game"
   - The app will connect to the server

### Bluetooth Connection

1. **Pair devices**
   - Enable Bluetooth on both PC and Android
   - On Android, scan for devices
   - Pair with your PC

2. **Configure the Android app**
   - Select "Bluetooth" as connection type
   - Port: 8887 (default)

3. **Connect**
   - Tap "Start Game"
   - The app will connect via Bluetooth

### USB Connection (Zero Latency)

#### Automatic Setup (Recommended)

1. **Enable USB Debugging on Android**
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Go to Settings → Developer Options
   - Enable "USB Debugging"

2. **Connect via USB**
   - Connect Android to PC via USB
   - Accept the debugging prompt on Android

3. **Start the server**
   - The server will automatically detect the device
   - ADB port forwarding will be set up automatically

4. **Configure the Android app**
   - Select "USB" as connection type
   - Port: 8890 (default)

5. **Connect**
   - Tap "Start Game"
   - Connection will be established via ADB

#### Manual Setup

If automatic setup fails:

1. **Install ADB**
   - Download Android Platform Tools
   - Extract and add to system PATH

2. **Set up port forwarding**
   ```bash
   adb devices
   adb forward tcp:8890 tcp:8890
   ```

3. **Start the server**
   ```bash
   python main.py
   ```

4. **Connect from the app**
   - Select "USB" as connection type
   - Tap "Start Game"

### Plug & Play Mode (Bluetooth HID)

1. **Enable Bluetooth on both devices**
   - Android: Enable Bluetooth
   - Host device: Enable Bluetooth and set to discoverable

2. **Start Plug & Play mode**
   - Open the app
   - Select "Plug & Play" mode
   - The app will advertise as a Bluetooth HID device

3. **Pair from host device**
   - On your host device (PC, TV, etc.)
   - Scan for Bluetooth devices
   - Select "Manette Gamepad"
   - Pair (no PIN required)

4. **Verification**
   - The host device will recognize it as a gamepad
   - Test in a game or controller test utility

**Note:** Plug & Play mode requires Android 9.0+ and may not work on all devices due to manufacturer restrictions.

---

## Troubleshooting

### Server Issues

**Server won't start**
- Ensure Python 3.10+ is installed
- Verify all dependencies: `pip list`
- Check if ports are already in use
- Run as Administrator if needed

**ViGEm Bus driver not working**
- Reinstall ViGEm Bus driver as Administrator
- Restart your computer after installation
- Check Device Manager for "ViGEm Bus Device"

**Connection refused**
- Check Windows Firewall settings
- Allow Python through firewall
- Verify IP address is correct
- Ensure both devices are on the same network

### Android Client Issues

**App won't install**
- Enable "Install from unknown sources"
- Check Android version compatibility (7.0+)
- Clear storage and reinstall

**Permissions denied**
- Grant all required permissions in Settings
- For Android 12+, grant "Nearby devices" permission
- Restart the app after granting permissions

**Connection failed**
- Verify server is running
- Check IP address and port
- Ensure both devices are on the same network
- Try different connection type (UDP → WebSocket)

**Bluetooth HID not working**
- Verify Android version is 9.0+
- Check if device supports HID profile
- Some manufacturers restrict HID access
- Try "The Great" mode as alternative

### Performance Issues

**High latency**
- Use Wi-Fi instead of Bluetooth
- Try USB connection for zero latency
- Reduce distance between devices
- Check network congestion

**Input lag**
- Adjust sensitivity settings
- Reduce deadzone values
- Close other apps on Android
- Use UDP instead of WebSocket

**Haptic feedback not working**
- Enable vibration in app settings
- Check device vibration motor
- Verify server has vibration enabled

### ADB Issues

**ADB not recognized**
- Install Android Platform Tools
- Add ADB to system PATH
- Restart Command Prompt after installation

**Device not found**
- Enable USB debugging on Android
- Accept debugging prompt
- Try different USB cable
- Restart ADB server: `adb kill-server && adb start-server`

**Port forwarding fails**
- Check if port is already in use
- Remove existing forwarding: `adb forward --remove tcp:8890`
- Run ADB as Administrator

---

## Next Steps

After successful installation:

1. **Configure your controller layout**
   - Open Layout Editor
   - Position buttons and joysticks
   - Customize appearance

2. **Adjust sensitivity**
   - Fine-tune joystick sensitivity
   - Set appropriate deadzones
   - Calibrate gyroscope if using motion controls

3. **Create profiles**
   - Save different configurations for different games
   - Import/export profiles for backup

4. **Test in games**
   - Start your game
   - Verify controller recognition
   - Adjust settings as needed

For more information, see [USAGE.md](USAGE.md) and [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
## Appairage Android UDP

1. Démarrez le serveur PHANTOM et générez un payload d'appairage temporaire
   depuis l'outil serveur prévu à cet effet (`server/core/pairing_payload.py`).
   Il contient une expiration et un token à usage unique ; ne le copiez pas
   dans les logs ou dans le dépôt.
2. Dans **Studio de configuration > Appairage du serveur**, collez le JSON
   temporaire puis choisissez **Enregistrer**. Le scanner caméra QR n'est pas
   encore intégré ; cette saisie est le parcours transitoire documenté.
3. Relancez la découverte ou **Connexion manuelle**. La reconnexion UDP utilise
   automatiquement le credential chiffré dans Android Keystore et ne passe à
   « connecté » qu'après `pair_ack`.
4. Pour changer de serveur ou invalider le téléphone, choisissez **Révoquer**.
   Le credential local est effacé et un nouvel appairage est requis.

Les modes WebSocket, USB et Bluetooth ne sont pas concernés par ce parcours.
