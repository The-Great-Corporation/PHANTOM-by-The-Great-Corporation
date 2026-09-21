package com.manette.hid

/**
 * Constructeur unique de rapports HID standard Gamepad 8 octets — Kotlin pur (P1-1 & P2-8).
 *
 * Résout le bug de dualité et de code mort entre [HidDeviceProfile.kt] (6 octets)
 * et le descripteur [BluetoothHidService.kt] (8 octets).
 *
 * Structure du rapport de 8 octets :
 *   Octet 0 : Boutons 0..7 (A, B, X, Y, LB, RB, Back, Start)
 *   Octet 1 : Boutons 8..15 (LS, RS, D-pad Up, Down, Left, Right, Guide, etc.)
 *   Octet 2 : Stick Gauche X (-127..127)
 *   Octet 3 : Stick Gauche Y (-127..127)
 *   Octet 4 : Stick Droit X / Z (-127..127)
 *   Octet 5 : Stick Droit Y / Rz (-127..127)
 *   Octet 6 : Gâchette Gauche Rx / LT (0..255)
 *   Octet 7 : Gâchette Droite Ry / RT (0..255)
 */
class HidReportBuilder {

    companion object {
        const val REPORT_SIZE = 8

        // Bitmasks pour boutons
        const val BTN_A = 1 shl 0
        const val BTN_B = 1 shl 1
        const val BTN_X = 1 shl 2
        const val BTN_Y = 1 shl 3
        const val BTN_LB = 1 shl 4
        const val BTN_RB = 1 shl 5
        const val BTN_BACK = 1 shl 6
        const val BTN_START = 1 shl 7
        const val BTN_LS = 1 shl 8
        const val BTN_RS = 1 shl 9
        const val BTN_DPAD_UP = 1 shl 10
        const val BTN_DPAD_DOWN = 1 shl 11
        const val BTN_DPAD_LEFT = 1 shl 12
        const val BTN_DPAD_RIGHT = 1 shl 13
    }

    /**
     * Construit un rapport HID de 8 octets conforme au descripteur.
     */
    fun buildReport(
        buttonsMask: Int,
        leftStickX: Float = 0f,
        leftStickY: Float = 0f,
        rightStickX: Float = 0f,
        rightStickY: Float = 0f,
        leftTrigger: Float = 0f,
        rightTrigger: Float = 0f
    ): ByteArray {
        val report = ByteArray(REPORT_SIZE)

        // Boutons 0 à 7
        report[0] = (buttonsMask and 0xFF).toByte()
        // Boutons 8 à 15
        report[1] = ((buttonsMask shr 8) and 0xFF).toByte()

        // Axes de sticks : normalisé [-1.0 .. 1.0] vers [-127 .. 127]
        fun floatToAxis(v: Float): Byte = (v * 127f).toInt().coerceIn(-127, 127).toByte()
        report[2] = floatToAxis(leftStickX)
        report[3] = floatToAxis(leftStickY)
        report[4] = floatToAxis(rightStickX)
        report[5] = floatToAxis(rightStickY)

        // Gâchettes analogiques : normalisé [0.0 .. 1.0] vers [0 .. 255]
        fun floatToTrigger(v: Float): Byte = (v * 255f).toInt().coerceIn(0, 255).toByte()
        report[6] = floatToTrigger(leftTrigger)
        report[7] = floatToTrigger(rightTrigger)

        return report
    }
}
