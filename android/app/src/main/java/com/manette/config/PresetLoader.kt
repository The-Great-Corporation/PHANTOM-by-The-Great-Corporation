package com.manette.config

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.manette.data.ControllerProfile

/**
 * Structure interne représentant un fichier preset JSON (assets/presets/*.json).
 * Gson ignore les champs inconnus — rétrocompatibilité garantie.
 */
private data class PresetJson(
    @SerializedName("schema_version") val schemaVersion: Int = 2,
    @SerializedName("preset_id") val presetId: String = "manette",
    @SerializedName("preset_name") val presetName: String = "Manette",
    @SerializedName("button_positions") val buttonPositions: Map<String, ButtonPositionJson> = emptyMap(),
    @SerializedName("button_mappings") val buttonMappings: Map<String, String> = emptyMap(),
    @SerializedName("joystick_settings") val joystickSettings: Map<String, JoystickConfigJson> = emptyMap(),
    @SerializedName("sensitivity_settings") val sensitivitySettings: SensitivityJson = SensitivityJson(),
    @SerializedName("deadzone_settings") val deadzoneSettings: DeadzoneJson = DeadzoneJson(),
    @SerializedName("skin") val skin: String = "xbox"
)

private data class ButtonPositionJson(
    @SerializedName("x") val x: Double = 0.0,
    @SerializedName("y") val y: Double = 0.0,
    @SerializedName("size") val size: Double = 1.0
)

private data class JoystickConfigJson(
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("sensitivity") val sensitivity: Double = 1.0,
    @SerializedName("deadzone") val deadzone: Double = 0.1,
    @SerializedName("invert_x") val invertX: Boolean = false,
    @SerializedName("invert_y") val invertY: Boolean = false
)

private data class SensitivityJson(
    @SerializedName("overall") val overall: Double = 1.0,
    @SerializedName("joystick") val joystick: Double = 1.0,
    @SerializedName("trigger") val trigger: Double = 1.0,
    @SerializedName("gyro") val gyro: Double = 1.0
)

private data class DeadzoneJson(
    @SerializedName("left_stick") val leftStick: Double = 0.1,
    @SerializedName("right_stick") val rightStick: Double = 0.1,
    @SerializedName("left_trigger") val leftTrigger: Double = 0.05,
    @SerializedName("right_trigger") val rightTrigger: Double = 0.05
)

/**
 * Charge et met en cache les presets depuis le dossier `assets/presets/`.
 * Utilisé par [ProfileManager] pour construire les nouveaux profils à partir de données,
 * et non de valeurs codées en dur.
 *
 * ### Règle R5
 * Tout layout, skin et réglage par défaut passe par ce loader.
 * Aucune constante de position ne doit apparaître dans le code Kotlin.
 */
object PresetLoader {
    private const val TAG = "PresetLoader"
    private const val PRESET_DIR = "presets"
    private const val DEFAULT_PRESET = "manette.json"

    private val gson = Gson()

    /**
     * Charge le preset par défaut ("manette") depuis les assets.
     * Retourne `null` si l'asset est absent ou malformé.
     */
    fun loadDefaultPreset(context: Context): ControllerProfile? =
        loadPreset(context, DEFAULT_PRESET)

    /**
     * Charge un preset nommé depuis `assets/presets/<filename>`.
     */
    fun loadPreset(context: Context, filename: String): ControllerProfile? {
        return try {
            val json = context.assets.open("$PRESET_DIR/$filename")
                .bufferedReader()
                .use { it.readText() }
            val preset = gson.fromJson(json, PresetJson::class.java)
            preset.toControllerProfile()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load preset '$filename': ${e.message}")
            null
        }
    }

    /**
     * Retourne la liste des fichiers de presets disponibles dans les assets.
     */
    fun listPresets(context: Context): List<String> {
        return try {
            context.assets.list(PRESET_DIR)?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list presets: ${e.message}")
            emptyList()
        }
    }

    // ── Conversion PresetJson → ControllerProfile ──────────────────────────────

    private fun PresetJson.toControllerProfile(): ControllerProfile {
        return ControllerProfile(
            name = presetName,
            schemaVersion = schemaVersion,
            buttonMappings = buttonMappings,
            joystickSettings = joystickSettings.mapValues { (_, v) ->
                JoystickConfig(
                    enabled = v.enabled,
                    sensitivity = v.sensitivity.toFloat(),
                    deadzone = v.deadzone.toFloat(),
                    invertX = v.invertX,
                    invertY = v.invertY
                )
            },
            sensitivitySettings = SensitivityConfig(
                overall = sensitivitySettings.overall.toFloat(),
                joystick = sensitivitySettings.joystick.toFloat(),
                trigger = sensitivitySettings.trigger.toFloat(),
                gyro = sensitivitySettings.gyro.toFloat()
            ),
            deadzoneSettings = DeadzoneConfig(
                leftStick = deadzoneSettings.leftStick.toFloat(),
                rightStick = deadzoneSettings.rightStick.toFloat(),
                leftTrigger = deadzoneSettings.leftTrigger.toFloat(),
                rightTrigger = deadzoneSettings.rightTrigger.toFloat()
            ),
            layoutConfig = LayoutConfig(
                buttonPositions = buttonPositions.mapValues { (_, v) ->
                    ButtonPosition(
                        x = v.x.toFloat(),
                        y = v.y.toFloat(),
                        size = v.size.toFloat()
                    )
                },
                skin = skin
            )
        )
    }
}
