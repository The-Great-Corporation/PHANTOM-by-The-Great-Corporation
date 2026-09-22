package com.manette.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.manette.config.ButtonPosition
import com.manette.config.LayoutConfig
import com.manette.config.ProfileManager
import com.manette.data.ControllerProfile
import com.manette.hid.BluetoothHidService
import com.manette.hid.HidCapability
import com.manette.hid.HidState
import com.manette.hid.HidStatus
import com.manette.network.ConnectionManager
import com.manette.network.ConnectionState
import com.manette.network.UdpClient
import com.manette.pairing.AndroidKeystoreCredentialStore
import com.manette.pairing.PairingCredentials
import com.manette.pairing.PairingPayloadParser
import com.manette.sensors.GyroscopeHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OperationMode {
    THE_GREAT,       // Serveur PC via Wi-Fi UDP / USB ADB
    PLUG_AND_PLAY    // Bluetooth HID natif (Phantom)
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val profileManager = ProfileManager(application)
    val connectionManager = ConnectionManager(application)
    private val credentialStore = AndroidKeystoreCredentialStore(application)
    private var pairingCredentials: PairingCredentials? = credentialStore.load()

    /** Gyroscope : données envoyées vers le serveur en mode The Great. */
    private val gyroscopeHandler = GyroscopeHandler(application).also { it.initialize() }

    init {
        // Câbler le callback vibration : serveur PC → téléphone vibre
        connectionManager.onVibrationReceived = { left, right, durationSec ->
            triggerDeviceVibration(left, right, durationSec)
        }
    }

    /**
     * Déclenche une vibration native sur le téléphone selon les paramètres reçus du serveur PC.
     * left/right : 0.0–1.0 ; durationSec : durée en secondes.
     */
    private fun triggerDeviceVibration(left: Float, right: Float, durationSec: Float) {
        val ctx = getApplication<Application>()
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = ((left + right) / 2f * 255f).toInt().coerceIn(1, 255)
            val durationMs = (durationSec * 1000f).toLong().coerceIn(10L, 3000L)
            val amp = if (vibrator.hasAmplitudeControl()) amplitude
                      else VibrationEffect.DEFAULT_AMPLITUDE
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amp))
        }
        Log.d("GameViewModel", "Rumble received: L=$left R=$right dur=${durationSec}s")
    }

    // ── Mode et état de connexion ────────────────────────────────────────────
    private val _operationMode = MutableStateFlow(OperationMode.THE_GREAT)
    val operationMode: StateFlow<OperationMode> = _operationMode.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    private val _serverIp = MutableStateFlow("192.168.1.100")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val _serverPort = MutableStateFlow("8888")
    val serverPort: StateFlow<String> = _serverPort.asStateFlow()

    private val _connectionType = MutableStateFlow("udp")
    val connectionType: StateFlow<String> = _connectionType.asStateFlow()

    private val _isAutoDiscovered = MutableStateFlow(false)
    val isAutoDiscovered: StateFlow<Boolean> = _isAutoDiscovered.asStateFlow()

    private val _pairingRequired = MutableStateFlow(pairingCredentials == null)
    val pairingRequired: StateFlow<Boolean> = _pairingRequired.asStateFlow()
    private val _pairingMessage = MutableStateFlow<String?>(null)
    val pairingMessage: StateFlow<String?> = _pairingMessage.asStateFlow()

    // ── PLUG & PLAY — Bluetooth HID ──────────────────────────────────────────
    private val _hidCapability = MutableStateFlow(BluetoothHidService.checkCapabilities(application))
    val hidCapability: StateFlow<HidCapability> = _hidCapability.asStateFlow()

    private val _hidState = MutableStateFlow(HidState())
    val hidState: StateFlow<HidState> = _hidState.asStateFlow()

    private val _hidConnected = MutableStateFlow(false)
    val hidConnected: StateFlow<Boolean> = _hidConnected.asStateFlow()

    private val _hidDeviceName = MutableStateFlow<String?>(null)
    val hidDeviceName: StateFlow<String?> = _hidDeviceName.asStateFlow()

    private var hidService: BluetoothHidService? = null
    private var hidServiceBound = false

    fun refreshHidCapabilities() {
        _hidCapability.value = BluetoothHidService.checkCapabilities(getApplication())
    }

    private val hidServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            // BluetoothHidService ne retourne pas de binder — on le récupère via broadcast
            Log.d("GameViewModel", "HID Service connected")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            hidService = null
            hidServiceBound = false
            _hidConnected.value = false
            Log.d("GameViewModel", "HID Service disconnected")
        }
    }

    // ── Profil et personnalisation ───────────────────────────────────────────
    private val _currentProfile = MutableStateFlow<ControllerProfile?>(null)
    val currentProfile: StateFlow<ControllerProfile?> = _currentProfile.asStateFlow()

    private val _backgroundUri = MutableStateFlow("")
    val backgroundUri: StateFlow<String> = _backgroundUri.asStateFlow()

    private val _backgroundDim = MutableStateFlow(0.35f)
    val backgroundDim: StateFlow<Float> = _backgroundDim.asStateFlow()

    private val _backgroundScale = MutableStateFlow(1.0f)
    val backgroundScale: StateFlow<Float> = _backgroundScale.asStateFlow()

    private val _backgroundOffsetX = MutableStateFlow(0.0f)
    val backgroundOffsetX: StateFlow<Float> = _backgroundOffsetX.asStateFlow()

    private val _backgroundOffsetY = MutableStateFlow(0.0f)
    val backgroundOffsetY: StateFlow<Float> = _backgroundOffsetY.asStateFlow()

    private val _skin = MutableStateFlow("xbox")
    val skin: StateFlow<String> = _skin.asStateFlow()

    private val _sensitivity = MutableStateFlow(1.0f)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private val _deadzone = MutableStateFlow(0.1f)
    val deadzone: StateFlow<Float> = _deadzone.asStateFlow()

    private val _quickSettingsOpen = MutableStateFlow(false)
    val quickSettingsOpen: StateFlow<Boolean> = _quickSettingsOpen.asStateFlow()

    private val _customLayout = MutableStateFlow<Map<String, ButtonPosition>>(emptyMap())
    val customLayout: StateFlow<Map<String, ButtonPosition>> = _customLayout.asStateFlow()

    // ── Multi-profils ────────────────────────────────────────────────────────
    private val _activeProfileFilename = MutableStateFlow("default_profile.json")
    val activeProfileFilename: StateFlow<String> = _activeProfileFilename.asStateFlow()

    private val _profilesList = MutableStateFlow<List<String>>(emptyList())
    val profilesList: StateFlow<List<String>> = _profilesList.asStateFlow()

    // ── Cache d'entrées temps réel ────────────────────────────────────────────
    private val inputState = mutableMapOf<String, Any>(
        "a" to false, "b" to false, "x" to false, "y" to false,
        "left_bumper" to false, "right_bumper" to false,
        "back" to false, "start" to false,
        "left_thumb" to false, "right_thumb" to false,
        "dpad_up" to false, "dpad_down" to false,
        "dpad_left" to false, "dpad_right" to false,
        "left_stick_x" to 0.0f, "left_stick_y" to 0.0f,
        "right_stick_x" to 0.0f, "right_stick_y" to 0.0f,
        "left_trigger" to 0.0f, "right_trigger" to 0.0f
    )

    init {
        viewModelScope.launch {
            val defaultProf = profileManager.createDefaultProfileIfNotExists()
            applyProfile(defaultProf, "default_profile.json")
            refreshProfilesList()

            // Détection automatique zéro-friction (mode THE GREAT uniquement)
            scanForTgcServer()
        }
    }

    // ── Découverte automatique (THE GREAT) ────────────────────────────────────
    fun scanForTgcServer() {
        viewModelScope.launch {
            Log.d("GameViewModel", "Zero-Friction: Scanning for PHANTOM server…")
            val discoveredIp = UdpClient.autoDiscoverServer()
            if (discoveredIp != null) {
                _serverIp.value = discoveredIp
                _isAutoDiscovered.value = true
                val credentials = pairingCredentials?.takeIf { it.isValid() }
                if (credentials == null) {
                    _pairingRequired.value = true
                    _pairingMessage.value = "Appairage requis avant la reconnexion UDP."
                    return@launch
                }
                _pairingRequired.value = false
                Log.d("GameViewModel", "Zero-Friction: Server found at $discoveredIp — reconnecting")
                connectionManager.connect("udp", discoveredIp, credentials.port,
                    credentials.deviceId, credentials.tokenId, credentials.tokenSecret)
            } else {
                _isAutoDiscovered.value = false
                Log.d("GameViewModel", "Zero-Friction: No PHANTOM server found on network")
            }
        }
    }

    // ── Démarrage du service HID (PLUG & PLAY) ────────────────────────────────
    fun startHidService() {
        refreshHidCapabilities()
        val cap = _hidCapability.value
        if (!cap.isSupportedOs) {
            _hidState.value = HidState(
                enabled = false,
                status = HidStatus.UNSUPPORTED_OS,
                errorMessage = "Android 9.0+ (API 28) est requis pour le mode Plug & Play."
            )
            return
        }
        if (!cap.hasBluetoothHardware) {
            _hidState.value = HidState(
                enabled = false,
                status = HidStatus.NO_BLUETOOTH_HARDWARE,
                errorMessage = "Aucune puce Bluetooth sur cet appareil."
            )
            return
        }
        if (!cap.isBluetoothEnabled) {
            _hidState.value = HidState(
                enabled = false,
                status = HidStatus.BLUETOOTH_DISABLED,
                errorMessage = "Veuillez activer le Bluetooth dans les paramètres."
            )
            return
        }

        val ctx = getApplication<Application>()
        val intent = Intent(ctx, BluetoothHidService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            Log.d("GameViewModel", "HID Service started — device visible as 'Phantom'")
            
            viewModelScope.launch {
                while (_operationMode.value == OperationMode.PLUG_AND_PLAY) {
                    val s = BluetoothHidService.instance?._hidState?.value
                    if (s != null) {
                        _hidState.value = s
                        _hidConnected.value = s.connected
                        _hidDeviceName.value = s.deviceName
                    }
                    kotlinx.coroutines.delay(1000)
                }
            }
        } catch (e: Exception) {
            Log.e("GameViewModel", "Error starting HID service: ${e.message}")
            _hidState.value = HidState(
                enabled = false,
                status = HidStatus.REGISTRATION_FAILED,
                errorMessage = "Erreur lancement service HID : ${e.message}"
            )
        }
    }

    fun stopHidService() {
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, BluetoothHidService::class.java))
        _hidConnected.value = false
        _hidDeviceName.value = null
        Log.d("GameViewModel", "HID Service stopped")
    }

    // ── Connexion selon le mode actif ─────────────────────────────────────────
    fun connectForCurrentMode() {
        when (_operationMode.value) {
            OperationMode.THE_GREAT -> {
                if (_isAutoDiscovered.value) {
                    val credentials = pairingCredentials?.takeIf { it.isValid() }
                    if (credentials == null) {
                        _pairingRequired.value = true
                        _pairingMessage.value = "Appairage requis avant la reconnexion UDP."
                        return
                    }
                    viewModelScope.launch {
                        connectionManager.connect("udp", _serverIp.value, credentials.port,
                            credentials.deviceId, credentials.tokenId, credentials.tokenSecret)
                    }
                } else {
                    // Pas encore découvert → relancer le scan
                    scanForTgcServer()
                }
            }
            OperationMode.PLUG_AND_PLAY -> {
                startHidService()
            }
        }
    }

    // ── Setters mode et réseau ────────────────────────────────────────────────
    fun setOperationMode(mode: OperationMode) {
        _operationMode.value = mode
    }

    fun setServerIp(ip: String) { _serverIp.value = ip }
    fun setServerPort(port: String) { _serverPort.value = port }
    fun setConnectionType(type: String) { _connectionType.value = type }

    fun connect() {
        viewModelScope.launch {
            val credentials = pairingCredentials?.takeIf { it.isValid() }
            if (_connectionType.value == "udp" && credentials == null) {
                _pairingRequired.value = true
                _pairingMessage.value = "Saisissez un payload d’appairage valide."
                return@launch
            }
            val portInt = credentials?.port ?: (_serverPort.value.toIntOrNull() ?: 8888)
            connectionManager.connect(_connectionType.value, _serverIp.value, portInt,
                credentials?.deviceId, credentials?.tokenId, credentials?.tokenSecret)
        }
    }

    fun savePairingPayload(raw: String): String? {
        return try {
            val credentials = PairingPayloadParser.parse(raw)
            credentialStore.save(credentials)
            pairingCredentials = credentials
            _serverIp.value = credentials.server
            _serverPort.value = credentials.port.toString()
            _pairingRequired.value = false
            _pairingMessage.value = "Appairage enregistré. Connexion au serveur…"
            if (_isAutoDiscovered.value) {
                viewModelScope.launch {
                    val connected = connectionManager.connect(
                        "udp",
                        _serverIp.value,
                        credentials.port,
                        credentials.deviceId,
                        credentials.tokenId,
                        credentials.tokenSecret
                    )
                    if (connected) {
                        _pairingMessage.value = "Serveur connecté."
                    } else {
                        _pairingMessage.value =
                            "Connexion impossible. Vérifiez que le serveur est démarré et que le QR n'est pas expiré."
                    }
                }
            }
            null
        } catch (error: IllegalArgumentException) {
            _pairingRequired.value = true
            _pairingMessage.value = error.message ?: "Payload d’appairage invalide."
            _pairingMessage.value
        }
    }

    fun clearPairing() {
        credentialStore.clear()
        pairingCredentials = null
        _pairingRequired.value = true
        _pairingMessage.value = "Appairage révoqué. Un nouveau payload est requis."
        viewModelScope.launch { connectionManager.disconnect() }
    }

    fun disconnect() {
        // Émettre l'état neutre avant la déconnexion (A5 : aucune touche collée)
        releaseAllInputs()
        gyroscopeHandler.stop()
        viewModelScope.launch { connectionManager.disconnect() }
        if (_operationMode.value == OperationMode.PLUG_AND_PLAY) stopHidService()
    }

    // ── Personnalisation ──────────────────────────────────────────────────────
    fun setBackgroundUri(uri: String) { _backgroundUri.value = uri; saveCurrentSettings() }
    fun setBackgroundDim(dim: Float) { _backgroundDim.value = dim.coerceIn(0f, 0.9f); saveCurrentSettings() }
    fun setBackgroundScale(scale: Float) { _backgroundScale.value = scale.coerceIn(1f, 5f); saveCurrentSettings() }
    fun setBackgroundOffsetX(v: Float) { _backgroundOffsetX.value = v.coerceIn(-1f, 1f); saveCurrentSettings() }
    fun setBackgroundOffsetY(v: Float) { _backgroundOffsetY.value = v.coerceIn(-1f, 1f); saveCurrentSettings() }

    /** Applique URI + tous les paramètres d'ajustement en une seule sauvegarde. */
    fun setBackground(uri: String, dim: Float, scale: Float = 1.0f, offsetX: Float = 0.0f, offsetY: Float = 0.0f) {
        _backgroundUri.value = uri
        _backgroundDim.value = dim.coerceIn(0f, 0.9f)
        _backgroundScale.value = scale.coerceIn(1f, 5f)
        _backgroundOffsetX.value = offsetX.coerceIn(-1f, 1f)
        _backgroundOffsetY.value = offsetY.coerceIn(-1f, 1f)
        saveCurrentSettings()
    }

    /** Met à jour uniquement les paramètres d'ajustement (sans changer l'URI). */
    fun applyBackgroundAdjustment(dim: Float, scale: Float, offsetX: Float, offsetY: Float) {
        _backgroundDim.value = dim.coerceIn(0f, 0.9f)
        _backgroundScale.value = scale.coerceIn(1f, 5f)
        _backgroundOffsetX.value = offsetX.coerceIn(-1f, 1f)
        _backgroundOffsetY.value = offsetY.coerceIn(-1f, 1f)
        saveCurrentSettings()
    }

    fun clearBackground() {
        _backgroundUri.value = ""
        _backgroundScale.value = 1.0f
        _backgroundOffsetX.value = 0.0f
        _backgroundOffsetY.value = 0.0f
        saveCurrentSettings()
    }
    fun setSkin(newSkin: String) { _skin.value = newSkin; saveCurrentSettings() }
    fun setSensitivity(sens: Float) { _sensitivity.value = sens; saveCurrentSettings() }
    fun setDeadzone(dz: Float) { _deadzone.value = dz; saveCurrentSettings() }
    fun toggleQuickSettings() { _quickSettingsOpen.value = !_quickSettingsOpen.value }
    fun closeQuickSettings() { _quickSettingsOpen.value = false }

    // ── Envoi des inputs ──────────────────────────────────────────────────────
    fun sendButtonPress(buttonKey: String, pressed: Boolean) {
        inputState[buttonKey] = pressed
        emitInput()
    }

    fun sendJoystickMove(stick: String, rawX: Float, rawY: Float) {
        val dz = _deadzone.value
        val sens = _sensitivity.value
        fun applyDeadzone(v: Float): Float {
            if (kotlin.math.abs(v) < dz) return 0f
            val sign = if (v > 0) 1f else -1f
            return ((kotlin.math.abs(v) - dz) / (1f - dz) * sign * sens).coerceIn(-1f, 1f)
        }
        val finalX = applyDeadzone(rawX)
        val finalY = applyDeadzone(rawY)
        if (stick == "left") {
            inputState["left_stick_x"] = finalX
            inputState["left_stick_y"] = finalY
        } else {
            inputState["right_stick_x"] = finalX
            inputState["right_stick_y"] = finalY
        }
        emitInput()
    }

    fun sendTrigger(triggerKey: String, value: Float) {
        inputState[triggerKey] = value.coerceIn(0f, 1f)
        emitInput()
    }

    private fun emitInput() {
        when (_operationMode.value) {
            OperationMode.THE_GREAT -> {
                // Inclure les données gyroscope si disponibles
                val gyroData = gyroscopeHandler.gyroData.value
                val payload = inputState.toMutableMap().apply {
                    if (gyroscopeHandler.enabled.value) {
                        put("gyro_x", gyroData.x)
                        put("gyro_y", gyroData.y)
                        put("gyro_z", gyroData.z)
                    }
                }
                viewModelScope.launch {
                    val sent = connectionManager.sendInput(payload)
                    Log.d("GameViewModel", "UDP input dispatched: sent=$sent")
                }
            }
            OperationMode.PLUG_AND_PLAY -> {
                sendHidReport()
            }
        }
    }

    /**
     * Remet tous les inputs à l'état neutre et l'émet vers le serveur / HID.
     * Appelé sur pause, écran éteint, annulation tactile ou perte de connexion (A5).
     */
    fun releaseAllInputs() {
        val booleanKeys = listOf(
            "a", "b", "x", "y", "left_bumper", "right_bumper",
            "back", "start", "left_thumb", "right_thumb",
            "dpad_up", "dpad_down", "dpad_left", "dpad_right"
        )
        val floatKeys = listOf(
            "left_stick_x", "left_stick_y", "right_stick_x", "right_stick_y",
            "left_trigger", "right_trigger"
        )
        booleanKeys.forEach { inputState[it] = false }
        floatKeys.forEach { inputState[it] = 0.0f }
        emitInput()
        Log.d("GameViewModel", "All inputs released — neutral state emitted")
    }

    /**
     * Construit et envoie un rapport HID Bluetooth correspondant à l'état courant.
     * Format : 2 octets boutons + 4 octets axes (Lx, Ly, Rx, Ry) + 2 octets triggers
     */
    private fun sendHidReport() {
        try {
            // ── Boutons (2 octets = 16 bits) ─────────────────────────────────
            var buttons: Int = 0
            val buttonMap = mapOf(
                "a" to 0, "b" to 1, "x" to 2, "y" to 3,
                "left_bumper" to 4, "right_bumper" to 5,
                "back" to 6, "start" to 7,
                "left_thumb" to 8, "right_thumb" to 9,
                "dpad_up" to 10, "dpad_down" to 11,
                "dpad_left" to 12, "dpad_right" to 13
            )
            buttonMap.forEach { (key, bit) ->
                if (inputState[key] == true) buttons = buttons or (1 shl bit)
            }

            // ── Axes sticks (-127..127) ───────────────────────────────────────
            fun floatToAxis(v: Float): Byte = (v * 127f).toInt().coerceIn(-127, 127).toByte()
            val lx = floatToAxis(inputState["left_stick_x"] as? Float ?: 0f)
            val ly = floatToAxis(inputState["left_stick_y"] as? Float ?: 0f)
            val rx = floatToAxis(inputState["right_stick_x"] as? Float ?: 0f)
            val ry = floatToAxis(inputState["right_stick_y"] as? Float ?: 0f)

            // ── Triggers (0..255) ─────────────────────────────────────────────
            fun floatToTrigger(v: Float): Byte = (v * 255f).toInt().coerceIn(0, 255).toByte()
            val lt = floatToTrigger(inputState["left_trigger"] as? Float ?: 0f)
            val rt = floatToTrigger(inputState["right_trigger"] as? Float ?: 0f)

            // ── Rapport HID complet ───────────────────────────────────────────
            val report = byteArrayOf(
                (buttons and 0xFF).toByte(),
                ((buttons shr 8) and 0xFF).toByte(),
                lx, ly, rx, ry, lt, rt
            )

            val sent = BluetoothHidService.instance?.sendInputReport(report) ?: false
            Log.i("GameViewModel", "HID report dispatched: buttons=$buttons lx=$lx ly=$ly (sentToHost=$sent)")
        } catch (e: Exception) {
            Log.e("GameViewModel", "HID report error: ${e.message}")
        }
    }

    // ── Gestion Multi-Profils & Persistance ─────────────────────────────────
    fun refreshProfilesList() {
        viewModelScope.launch {
            _profilesList.value = profileManager.listProfiles()
        }
    }

    private fun applyProfile(profile: ControllerProfile, filename: String) {
        _currentProfile.value = profile
        _activeProfileFilename.value = filename
        _backgroundUri.value = profile.layoutConfig.backgroundPath
        _backgroundDim.value = profile.layoutConfig.backgroundDim
        _backgroundScale.value = profile.layoutConfig.backgroundScale
        _backgroundOffsetX.value = profile.layoutConfig.backgroundOffsetX
        _backgroundOffsetY.value = profile.layoutConfig.backgroundOffsetY
        _skin.value = profile.layoutConfig.skin.ifEmpty { "xbox" }
        _sensitivity.value = profile.sensitivitySettings.overall
        _deadzone.value = profile.deadzoneSettings.leftStick
        _customLayout.value = profile.layoutConfig.buttonPositions
    }

    fun switchProfile(filename: String) {
        viewModelScope.launch {
            val prof = profileManager.loadProfile(filename)
            if (prof != null) {
                applyProfile(prof, filename)
            }
        }
    }

    fun createNewProfile(name: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val cleanName = name.trim().ifEmpty { "Nouveau Profil" }
            val sanitized = cleanName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val filename = "${sanitized}_${System.currentTimeMillis() % 10000}.json"
            val base = _currentProfile.value ?: profileManager.createDefaultProfileIfNotExists()
            val newProf = base.copy(
                name = cleanName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val success = profileManager.saveProfile(newProf, filename)
            if (success) {
                applyProfile(newProf, filename)
                refreshProfilesList()
            }
            onComplete?.invoke(success)
        }
    }

    fun duplicateCurrentProfile(newName: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val cleanName = newName.trim().ifEmpty { "${_currentProfile.value?.name ?: "Profil"} (Copie)" }
            val sanitized = cleanName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val filename = "${sanitized}_${System.currentTimeMillis() % 10000}.json"
            val success = profileManager.duplicateProfile(_activeProfileFilename.value, cleanName, filename)
            if (success) {
                switchProfile(filename)
                refreshProfilesList()
            }
            onComplete?.invoke(success)
        }
    }

    fun renameCurrentProfile(newName: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val cleanName = newName.trim()
            if (cleanName.isEmpty()) {
                onComplete?.invoke(false)
                return@launch
            }
            val filename = _activeProfileFilename.value
            val success = profileManager.renameProfile(filename, cleanName)
            if (success) {
                val current = _currentProfile.value
                if (current != null) {
                    _currentProfile.value = current.copy(name = cleanName, updatedAt = System.currentTimeMillis())
                }
                refreshProfilesList()
            }
            onComplete?.invoke(success)
        }
    }

    fun deleteCurrentProfile(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val current = _activeProfileFilename.value
            if (current == "default_profile.json") {
                onComplete?.invoke(false)
                return@launch
            }
            val success = profileManager.deleteProfile(current)
            if (success) {
                switchProfile("default_profile.json")
                refreshProfilesList()
            }
            onComplete?.invoke(success)
        }
    }

    suspend fun exportActiveProfileToStream(outputStream: java.io.OutputStream): Boolean {
        val cur = _currentProfile.value ?: return false
        return profileManager.exportProfileToStream(cur, outputStream)
    }

    suspend fun importProfileFromStream(inputStream: java.io.InputStream, suggestedFilename: String): Boolean {
        val imported = profileManager.importProfileFromStream(inputStream) ?: return false
        val filename = if (suggestedFilename.endsWith(".json") || suggestedFilename.endsWith(".phantom")) {
            suggestedFilename
        } else {
            "${suggestedFilename}.json"
        }
        val saved = profileManager.saveProfile(imported, filename)
        if (saved) {
            applyProfile(imported, filename)
            refreshProfilesList()
        }
        return saved
    }

    // ── Position des boutons (layout éditeur) ─────────────────────────────────
    fun updateAllButtonPositions(positions: Map<String, ButtonPosition>) {
        _customLayout.value = positions
        val cur = _currentProfile.value ?: return
        val updated = cur.copy(layoutConfig = cur.layoutConfig.copy(buttonPositions = positions))
        _currentProfile.value = updated
        viewModelScope.launch { profileManager.saveProfile(updated, _activeProfileFilename.value) }
    }

    fun resetCustomLayout() {
        _customLayout.value = emptyMap()
        val cur = _currentProfile.value ?: return
        val updated = cur.copy(layoutConfig = cur.layoutConfig.copy(buttonPositions = emptyMap()))
        _currentProfile.value = updated
        viewModelScope.launch { profileManager.saveProfile(updated, _activeProfileFilename.value) }
    }

    fun updateButtonPosition(buttonId: String, x: Float, y: Float) {
        val cur = _currentProfile.value ?: return
        val currentPositions = cur.layoutConfig.buttonPositions.toMutableMap()
        val oldPos = currentPositions[buttonId]
        currentPositions[buttonId] = ButtonPosition(x = x, y = y, size = oldPos?.size ?: 1.0f)
        val updated = cur.copy(layoutConfig = cur.layoutConfig.copy(buttonPositions = currentPositions))
        _currentProfile.value = updated
        _customLayout.value = currentPositions
        viewModelScope.launch { profileManager.saveProfile(updated, _activeProfileFilename.value) }
    }

    private fun saveCurrentSettings() {
        val cur = _currentProfile.value ?: return
        val updated = cur.copy(
            layoutConfig = cur.layoutConfig.copy(
                backgroundPath = _backgroundUri.value,
                backgroundDim = _backgroundDim.value,
                backgroundScale = _backgroundScale.value,
                backgroundOffsetX = _backgroundOffsetX.value,
                backgroundOffsetY = _backgroundOffsetY.value,
                skin = _skin.value
            ),
            sensitivitySettings = cur.sensitivitySettings.copy(overall = _sensitivity.value),
            deadzoneSettings = cur.deadzoneSettings.copy(leftStick = _deadzone.value)
        )
        _currentProfile.value = updated
        viewModelScope.launch { profileManager.saveProfile(updated, _activeProfileFilename.value) }
    }

    override fun onCleared() {
        super.onCleared()
        gyroscopeHandler.stop()
        if (hidServiceBound) {
            try { getApplication<Application>().unbindService(hidServiceConnection) } catch (_: Exception) {}
        }
    }
}
