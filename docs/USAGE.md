# Manette - Usage Guide

Complete usage guide for the Virtual Gamepad System.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Mode Selection](#mode-selection)
3. [Configuration](#configuration)
4. [Game Screen](#game-screen)
5. [Layout Editor](#layout-editor)
6. [Profile Management](#profile-management)
7. [Advanced Features](#advanced-features)

---

## Getting Started

### First Launch

1. **Launch the Python Server**
   ```bash
   cd server
   python main.py
   ```
   Keep this running while using the app.

2. **Launch the Android App**
   - Open the Manette app on your Android device
   - You'll see the Mode Selection screen

3. **Choose Your Mode**
   - **"The Great"** - Advanced mode with PC server
   - **"Plug & Play"** - Native Bluetooth HID mode

---

## Mode Selection

### "The Great" Mode

This mode connects to the Python server on your PC for advanced features.

**Features:**
- Multi-connection support (Wi-Fi, Bluetooth, USB)
- Xbox 360/DualShock 4 emulation
- Haptic feedback
- Zero-latency USB via ADB

**When to use:**
- Playing PC games
- Need advanced features
- Want lowest latency

### "Plug & Play" Mode

This mode uses native Android Bluetooth HID to emulate a physical controller.

**Features:**
- No server required
- Universal compatibility
- Instant recognition
- Works with any host device
- Simple setup

**When to use:**
- Connecting to TV, console, or other devices
- Don't want to run a server
- Need universal compatibility
- Simple use case

**Limitations:**
- Requires Android 9.0+
- May not work on all devices
- Fewer customization options

---

## Configuration

### Connection Settings

Access via: **Configuration → Connection Settings**

**Server IP**
- Enter your PC's IP address
- Find it with `ipconfig` on Windows
- Example: `192.168.1.100`

**Server Port**
- Default: `8888` (UDP)
- Default: `8889` (WebSocket)
- Default: `8887` (Bluetooth)
- Default: `8890` (USB)

**Connection Type**
- **UDP** - Lowest latency, recommended for Wi-Fi
- **WebSocket** - More reliable, fallback option
- **Bluetooth** - For Bluetooth connections

UDP pairing is authenticated and requires the caller to inject `device_id`,
`token_id`, and `token_secret`. Missing credentials fail closed; discovery
alone does not authorize input. Credentials are not stored or logged. QR-code
provisioning and Android Keystore support are planned, so use a protected
in-memory provisioning flow until they are available.
- **USB** - Zero latency via ADB

### Sensitivity Settings

Access via: **Configuration → Sensitivity Settings**

**Joystick Sensitivity**
- Range: 0.1x to 3.0x
- Default: 1.0x
- Higher = faster movement
- Lower = more precise control

**Deadzone**
- Range: 0% to 50%
- Default: 10%
- Eliminates drift from loose joysticks
- Higher = less sensitive near center

### Feature Toggles

Access via: **Configuration → Features**

**Haptic Feedback**
- Enable/disable vibration
- Requires device with vibration motor
- Server must have vibration enabled

**Gyroscope Controls**
- Enable motion-based controls
- Requires device with gyroscope
- Calibrate before use

---

## Game Screen

### Overview

The game screen displays your virtual controller with all buttons and joysticks.

### Controller Layout

**Left Side:**
- Left Joystick (movement)
- D-Pad (directional)
- Left Bumper (LB)

**Right Side:**
- Right Joystick (camera/aiming)
- Action Buttons (A, B, X, Y)
- Right Bumper (RB)

**Center:**
- Back Button
- Start Button

### Using the Controller

**Buttons**
- Tap to press
- Release to release
- Visual feedback when pressed

**Joysticks**
- Touch and drag to move
- Returns to center when released
- Visual indicator of position

**Status Bar**
- Connection status (green/red dot)
- Latency in milliseconds
- Connection type badge
- Battery indicator

### Gyroscope Controls

When enabled:
- Tilt device to control camera/aiming
- Calibrate before first use
- Adjust sensitivity in settings

**Calibration:**
1. Place device on flat surface
2. Tap "Calibrate" in settings
3. Device will set current orientation as neutral

---

## Layout Editor

### Overview

Customize button positions, sizes, and appearance.

### Access

Via: **Configuration → Open Layout Editor**

### Editing Buttons

**Move Buttons**
- Touch and drag button to desired position
- Position is saved automatically

**Resize Buttons**
- Select button
- Adjust size slider in properties panel
- Size is saved automatically

**Reset Position**
- Select button
- Tap "Reset Position" in properties
- Returns to default position

### Customizing Appearance

**Background**
- Import custom image or GIF
- Supports common formats (JPG, PNG, GIF)
- Tap background to select file

**Button Skins**
- Import custom button graphics
- Supports transparent PNG
- Apply to individual buttons

### Saving Layouts

- Tap "Save" in top-right corner
- Layout is saved to current profile
- Can create multiple profiles

---

## Profile Management

### Creating Profiles

1. Configure settings to your liking
2. Go to Configuration
3. Tap "Save Profile"
4. Enter profile name
5. Profile is saved

### Loading Profiles

1. Go to Configuration
2. Tap "Load Profile"
3. Select profile from list
4. Settings are applied

### Exporting Profiles

1. Go to Configuration
2. Tap "Export Profile"
3. Select profile to export
4. Choose destination
5. Profile saved as JSON file

### Importing Profiles

1. Go to Configuration
2. Tap "Import Profile"
3. Select JSON file
4. Profile is loaded
5. Tap "Save" to keep it

### Default Profile

The app automatically creates a default profile on first launch with standard Xbox 360 layout.

---

## Advanced Features

### Haptic Feedback

The server can send vibration commands to your Android device.

**Triggers:**
- Game events (collision, explosion, etc.)
- Manual triggers from server
- Button presses

**Configuration:**
- Enable in app settings
- Enable in server config
- Adjust intensity if needed

### Macros

Record and replay button sequences.

**Recording:**
1. Long-press a button
2. Perform button sequence
3. Release to stop recording
4. Sequence is saved to that button

**Playback:**
- Tap the button to replay sequence
- Hold for continuous playback

### Reconnection

The app automatically reconnects if connection is lost.

**Settings:**
- Reconnect interval: 5 seconds (default)
- Max attempts: Unlimited
- Can be disabled in settings

---

## Tips and Best Practices

### For Best Performance

1. **Use Wi-Fi for lowest latency**
   - 5GHz Wi-Fi is better than 2.4GHz
   - Stay close to router
   - Avoid network congestion

2. **Use USB for zero latency**
   - Requires ADB setup
   - Best for competitive gaming
   - Most reliable connection

3. **Adjust deadzone**
   - Higher deadzone reduces drift
   - Lower deadzone increases precision
   - Find the right balance

4. **Calibrate sensors**
   - Calibrate gyroscope before use
   - Reset calibration if drifting occurs
   - Place on flat surface for calibration

### Battery Optimization

1. **Reduce screen brightness**
2. **Disable unused features**
3. **Use Wi-Fi instead of cellular**
4. **Close background apps**

### Troubleshooting Common Issues

**Input lag:**
- Switch to UDP connection
- Try USB connection
- Reduce distance between devices
- Close other apps

**Connection drops:**
- Check network stability
- Try different connection type
- Ensure server is running
- Restart both devices

**Buttons not responding:**
- Check connection status
- Verify server is receiving input
- Restart the app
- Reconfigure button mappings

---

## Keyboard Shortcuts (Server)

When the server is running:

- `Ctrl+C` - Stop server
- Check logs for connection status
- Monitor latency in logs

---

For troubleshooting specific issues, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
