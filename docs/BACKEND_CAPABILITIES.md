# Backend capabilities

This matrix describes what is implemented in the local source tree. It is not a
promise about a platform feature that is only named in a dependency or a
protocol message.

## Status vocabulary

- **supported**: the input/output path is implemented in the inspected code.
- **partial**: one side of the path exists, but the end-to-end behavior is not
  demonstrated by the local client and server.
- **unsupported**: no local implementation consumes or produces the capability.

The authoritative programmatic representation is
`server/core/backend_capabilities.py`. `get_capability_matrix()` returns these
three string values, not booleans.

## Matrix

| Capability | Xbox 360 | DualShock 4 | Bluetooth HID | UDP | WebSocket | USB/ADB | Bluetooth RFCOMM |
|---|---|---|---|---|---|---|---|
| Gamepad buttons in `GamepadState` | supported | unsupported | supported | supported | supported | supported | supported |
| Analog triggers/sticks | supported | unsupported | supported | supported | supported | supported | supported |
| Guide/touchpad/PS buttons | unsupported | unsupported | unsupported | unsupported | unsupported | unsupported | unsupported |
| Vibration output to client | unsupported | unsupported | unsupported | partial | partial | partial | unsupported |
| Native vibration feedback | supported | unsupported | unsupported | partial | partial | partial | unsupported |
| LED control | unsupported | unsupported | unsupported | unsupported | unsupported | unsupported | unsupported |
| Gyroscope input | unsupported | unsupported | unsupported | unsupported | unsupported | unsupported | unsupported |
| Multiple independent clients | supported (4 slots) | supported (4 slots) | unsupported (1 host) | supported (configured limit) | supported (configured limit) | supported (configured limit) | unsupported (shared state) |

### Why the partial network vibration status is intentional

The server transports can emit vibration JSON after receiving a native
vibration notification. UDP's Android client parses a vibration message, but
the server currently emits `left`/`right` while that client reads
`left_motor`/`right_motor`. WebSocket and USB clients do not read vibration
messages. Therefore this capability is not marked supported and this roadmap
step does not add a protocol change.

### Backend-specific findings

#### Xbox 360 (`xbox360`)

`GamepadEmulator.update_state()` calls the XUSB button, trigger, and joystick
methods. Four XInput slots are allocated and native rumble notifications are
registered for Xbox instances. No LED method is called, and the Guide button is
never updated. The backend therefore supports the standard 20-field
`GamepadState`, native vibration feedback, and four slots only.

#### DualShock 4 (`dualshock4`)

The emulator can construct `VDS4Gamepad`, but its update path still calls
`vg.XUSB_BUTTON` constants and Xbox update methods. DS4 button/axis mappings
declared in the previous matrix are consequently not demonstrable and are
marked unsupported. No DS4 rumble or light-bar API is called.

#### Bluetooth HID (`bluetooth_hid`)

This is Android acting as a Bluetooth HID device, not the Python RFCOMM
transport. [`HidReportBuilder.kt`](../android/app/src/main/java/com/manette/hid/HidReportBuilder.kt)
and [`BluetoothHidService.kt`](../android/app/src/main/java/com/manette/hid/BluetoothHidService.kt)
build and send the eight-byte input report containing the 20 standard state
fields. The descriptor has no output report, vibration, LED, sensor, or
multi-host path.

#### UDP, WebSocket, and USB/ADB

These Python servers construct `GamepadState` from the 20 JSON input fields and
forward it to the emulator. They do not consume gyro fields. Their connection
managers can accept multiple clients, subject to the configured server limit.
The Android implementations send input and heartbeat messages; only
`UdpClient` currently parses a vibration message, with the field-name mismatch
described above.

#### Bluetooth RFCOMM (`bluetooth_rfcomm`)

The Python server accepts newline-delimited JSON, but the Android
`BluetoothClient.connect()` entry point is still a device-selection placeholder
and returns false. `connectToDevice()` is a separate path, and the server calls
`update_state()` without a client slot, so connected clients would share slot 1.
It is therefore not equivalent to Bluetooth HID and is not advertised as a
fully supported multi-client backend.

## Verification contract

The targeted server test suite checks:

1. every declared backend has an explicit status for each matrix feature;
2. DS4 controls and Guide are not reported as supported when their update
   paths are absent;
3. gyro input stays unsupported until a transport and `GamepadState` consumer
   exist;
4. network vibration remains partial until both server emission and client
   consumption agree.

Run from the repository root:

```text
python -m unittest discover -s server/tests -p "test_*_contract.py" -v
python -m unittest discover -s server/tests -v
python -m compileall -q server
```
