package com.manette.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calculs mathématiques pour sticks analogiques — Kotlin pur, sans dépendance Android (P1-1).
 *
 * Exécutable et testable en JVM.
 */
object StickMath {

    data class StickOffset(val x: Float, val y: Float)
    data class NormalizedStick(val x: Float, val y: Float)

    /**
     * Contraint un décalage (dx, dy) à l'intérieur d'un rayon maximal [maxRadiusPx].
     */
    fun clampOffset(dx: Float, dy: Float, maxRadiusPx: Float): StickOffset {
        val distance = sqrt(dx * dx + dy * dy)
        return if (distance > maxRadiusPx && maxRadiusPx > 0f) {
            val angle = atan2(dy, dx)
            StickOffset(cos(angle) * maxRadiusPx, sin(angle) * maxRadiusPx)
        } else {
            StickOffset(dx, dy)
        }
    }

    /**
     * Normalise les coordonnées de stick dans la plage [-1.0f .. 1.0f].
     * Inversion de l'axe Y conforme aux conventions manette :
     * pousser le stick vers le haut (Y négatif en écran) donne un Y positif manette (+1.0f).
     */
    fun normalize(dx: Float, dy: Float, maxRadiusPx: Float): NormalizedStick {
        if (maxRadiusPx <= 0f) return NormalizedStick(0f, 0f)
        val clamped = clampOffset(dx, dy, maxRadiusPx)
        val normX = (clamped.x / maxRadiusPx).coerceIn(-1f, 1f)
        val normY = -(clamped.y / maxRadiusPx).coerceIn(-1f, 1f)
        return NormalizedStick(normX, normY)
    }

    /**
     * Applique une zone morte intérieure et une sensibilité.
     */
    fun applyDeadzoneAndSensitivity(
        value: Float,
        deadzone: Float = 0.1f,
        sensitivity: Float = 1.0f
    ): Float {
        val absVal = kotlin.math.abs(value)
        if (absVal < deadzone) return 0f
        val sign = if (value > 0) 1f else -1f
        val effectiveRange = (1f - deadzone).coerceAtLeast(0.0001f)
        return ((absVal - deadzone) / effectiveRange * sign * sensitivity).coerceIn(-1f, 1f)
    }
}
