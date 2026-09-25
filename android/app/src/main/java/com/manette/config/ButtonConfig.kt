package com.manette.config

import com.google.gson.annotations.SerializedName

data class ButtonPosition(
    @SerializedName("x")
    val x: Float,
    
    @SerializedName("y")
    val y: Float,
    
    @SerializedName("size")
    val size: Float
)

data class JoystickConfig(
    @SerializedName("enabled")
    val enabled: Boolean = true,
    
    @SerializedName("sensitivity")
    val sensitivity: Float = 1.0f,
    
    @SerializedName("deadzone")
    val deadzone: Float = 0.1f,
    
    @SerializedName("invert_x")
    val invertX: Boolean = false,
    
    @SerializedName("invert_y")
    val invertY: Boolean = false
)

data class SensitivityConfig(
    @SerializedName("overall")
    val overall: Float = 1.0f,
    
    @SerializedName("joystick")
    val joystick: Float = 1.0f,
    
    @SerializedName("trigger")
    val trigger: Float = 1.0f,
    
    @SerializedName("gyro")
    val gyro: Float = 1.0f
)

data class DeadzoneConfig(
    @SerializedName("left_stick")
    val leftStick: Float = 0.1f,
    
    @SerializedName("right_stick")
    val rightStick: Float = 0.1f,
    
    @SerializedName("left_trigger")
    val leftTrigger: Float = 0.05f,
    
    @SerializedName("right_trigger")
    val rightTrigger: Float = 0.05f
)

data class LayoutConfig(
    @SerializedName("button_positions")
    val buttonPositions: Map<String, ButtonPosition> = emptyMap(),
    
    @SerializedName("background_path")
    val backgroundPath: String = "",
    
    @SerializedName("background_dim")
    val backgroundDim: Float = 0.35f,
    
    @SerializedName("background_scale")
    val backgroundScale: Float = 1.0f,
    
    @SerializedName("background_offset_x")
    val backgroundOffsetX: Float = 0.0f,
    
    @SerializedName("background_offset_y")
    val backgroundOffsetY: Float = 0.0f,
    
    @SerializedName("skin")
    val skin: String = "default",

    @SerializedName("floating_sticks")
    val floatingSticks: Boolean = true
)


object LayoutDefaults {
    val defaultPositions: Map<String, ButtonPosition> = mapOf(
        "btn_lt" to ButtonPosition(x = 0.08f, y = 0.12f, size = 1.0f),
        "btn_lb" to ButtonPosition(x = 0.18f, y = 0.12f, size = 1.0f),
        "btn_back" to ButtonPosition(x = 0.44f, y = 0.12f, size = 1.0f),
        "btn_start" to ButtonPosition(x = 0.56f, y = 0.12f, size = 1.0f),
        "btn_rb" to ButtonPosition(x = 0.82f, y = 0.12f, size = 1.0f),
        "btn_rt" to ButtonPosition(x = 0.92f, y = 0.12f, size = 1.0f),
        "left_stick" to ButtonPosition(x = 0.13f, y = 0.65f, size = 1.0f),
        "dpad" to ButtonPosition(x = 0.32f, y = 0.65f, size = 1.0f),
        "right_stick" to ButtonPosition(x = 0.68f, y = 0.65f, size = 1.0f),
        "abxy" to ButtonPosition(x = 0.88f, y = 0.65f, size = 1.0f)
    )

    val controlLabels: Map<String, String> = mapOf(
        "btn_lt" to "Gâchette Gauche (LT)",
        "btn_lb" to "Bumper Gauche (LB)",
        "btn_back" to "Touche Back",
        "btn_start" to "Touche Start",
        "btn_rb" to "Bumper Droit (RB)",
        "btn_rt" to "Gâchette Droite (RT)",
        "left_stick" to "Stick Analogique Gauche",
        "dpad" to "Croix Directionnelle (D-Pad)",
        "right_stick" to "Stick Analogique Droit",
        "abxy" to "Touches d'action (ABXY)"
    )

    fun getEffectivePositions(custom: Map<String, ButtonPosition>): Map<String, ButtonPosition> {
        val effective = defaultPositions.mapValues { (key, defaultPos) ->
            custom[key] ?: defaultPos
        }.toMutableMap()

        // Rétrocompatibilité : si l'ancien profil sauvegardait btn_a/btn_y individuellement au lieu de abxy
        if (!custom.containsKey("abxy")) {
            val legacyCenterY = custom["btn_x"]?.y ?: custom["btn_b"]?.y
            val legacyCenterX = custom["btn_y"]?.x ?: custom["btn_a"]?.x
            val legacySize = custom["btn_a"]?.size ?: custom["btn_x"]?.size
            if (legacyCenterX != null && legacyCenterY != null) {
                effective["abxy"] = ButtonPosition(legacyCenterX, legacyCenterY, legacySize ?: 1.0f)
            }
        }
        return effective
    }
}
