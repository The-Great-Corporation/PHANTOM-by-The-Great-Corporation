package com.manette

import com.manette.math.StickMath
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests JVM unitaires pour [StickMath] (P1-1).
 */
class StickMathTest {

    @Test
    fun `clampOffset limite la distance au rayon max`() {
        val maxRadius = 50f
        // Point à distance 100 sur l'axe X
        val clamped = StickMath.clampOffset(100f, 0f, maxRadius)
        assertEquals(50f, clamped.x, 0.001f)
        assertEquals(0f, clamped.y, 0.001f)

        // Point à l'intérieur du rayon
        val inside = StickMath.clampOffset(20f, 20f, maxRadius)
        assertEquals(20f, inside.x, 0.001f)
        assertEquals(20f, inside.y, 0.001f)
    }

    @Test
    fun `normalize inverse l axe Y conformement aux conventions gamepad`() {
        val maxRadius = 100f
        // Poussée vers le haut (Y négatif en écran) -> Y positif (+1.0)
        val up = StickMath.normalize(0f, -100f, maxRadius)
        assertEquals(0f, up.x, 0.001f)
        assertEquals(1.0f, up.y, 0.001f)

        // Poussée vers le bas (Y positif en écran) -> Y négatif (-1.0)
        val down = StickMath.normalize(0f, 100f, maxRadius)
        assertEquals(0f, down.x, 0.001f)
        assertEquals(-1.0f, down.y, 0.001f)

        // Poussée à droite -> X positif (+1.0)
        val right = StickMath.normalize(100f, 0f, maxRadius)
        assertEquals(1.0f, right.x, 0.001f)
        assertEquals(0f, right.y, 0.001f)
    }

    @Test
    fun `applyDeadzone filtre les petites valeurs et remappe la plage`() {
        val deadzone = 0.15f
        // Valeur sous la deadzone -> 0
        assertEquals(0f, StickMath.applyDeadzoneAndSensitivity(0.10f, deadzone), 0.001f)
        assertEquals(0f, StickMath.applyDeadzoneAndSensitivity(-0.10f, deadzone), 0.001f)

        // Valeur max -> 1.0
        assertEquals(1.0f, StickMath.applyDeadzoneAndSensitivity(1.0f, deadzone), 0.001f)
        assertEquals(-1.0f, StickMath.applyDeadzoneAndSensitivity(-1.0f, deadzone), 0.001f)

        // Valeur intermédiaire
        val mid = StickMath.applyDeadzoneAndSensitivity(0.575f, deadzone)
        assertTrue("Valeur intermédiaire filtrée positive", mid > 0f && mid < 1f)
    }
}
