package com.manette

import com.manette.config.ButtonPosition
import com.manette.config.LayoutConfig
import com.manette.config.LayoutValidator
import com.manette.data.ControllerProfile
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests JVM pour [LayoutValidator].
 *
 *   ./gradlew testDebugUnitTest --tests "com.manette.LayoutValidatorTest"
 *
 * Critère P0-4 / A10 : l'éditeur doit bloquer l'enregistrement si une zone
 * tactile effective est < 48 dp.
 */
class LayoutValidatorTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun profile(positions: Map<String, ButtonPosition>) = ControllerProfile(
        name = "test",
        layoutConfig = LayoutConfig(buttonPositions = positions)
    )

    // ── Taille minimale ──────────────────────────────────────────────────────

    @Test
    fun `positions valides passent la validation`() {
        val positions = mapOf(
            "btn_lt"    to ButtonPosition(0.08f, 0.12f, 1.0f),   // 54 dp ✔
            "btn_lb"    to ButtonPosition(0.18f, 0.12f, 1.0f),   // 54 dp ✔
            "btn_back"  to ButtonPosition(0.44f, 0.12f, 1.1f),   // 50.6 dp ✔
            "dpad"      to ButtonPosition(0.32f, 0.65f, 1.15f),  // 48.3 dp ✔
            "abxy"      to ButtonPosition(0.88f, 0.65f, 1.05f),  // 48.3 dp ✔
        )
        val result = LayoutValidator.validatePositions(positions)
        assertTrue("Positions valides → aucune violation", result.isValid)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `P0-4 zone tactile trop petite genere une violation`() {
        // D-Pad à size=0.60 : 42 * 0.60 = 25.2 dp → violation
        val positions = mapOf(
            "dpad" to ButtonPosition(0.32f, 0.65f, 0.60f)
        )
        val result = LayoutValidator.validatePositions(positions)
        assertFalse("Taille insuffisante → invalide", result.isValid)
        assertEquals(1, result.violations.size)
        assertEquals("dpad", result.violations[0].controlKey)
        assertTrue(result.violations[0].message.contains("25"))  // 25 dp
    }

    @Test
    fun `P0-4 abxy a size 0_60 genere une violation`() {
        // ABXY à size=0.60 : 46 * 0.60 = 27.6 dp → violation
        val positions = mapOf(
            "abxy" to ButtonPosition(0.88f, 0.65f, 0.60f)
        )
        val result = LayoutValidator.validatePositions(positions)
        assertFalse(result.isValid)
        assertEquals(1, result.violations.size)
    }

    @Test
    fun `P0-4 btn_lt a taille limite exacte 48dp valide`() {
        // LT à size = 48/54 ≈ 0.889 → exactement 48 dp → valide
        val size = 48f / 54f
        val positions = mapOf(
            "btn_lt" to ButtonPosition(0.08f, 0.12f, size)
        )
        val result = LayoutValidator.validatePositions(positions)
        assertTrue("48 dp exact → valide", result.isValid)
    }

    @Test
    fun `P0-4 btn_lt sous le seuil minimum genere violation`() {
        // LT à size = 47/54 → 47 dp → violation
        val size = 47f / 54f
        val positions = mapOf(
            "btn_lt" to ButtonPosition(0.08f, 0.12f, size)
        )
        val result = LayoutValidator.validatePositions(positions)
        assertFalse(result.isValid)
        assertEquals("btn_lt", result.violations[0].controlKey)
    }

    @Test
    fun `plusieurs violations detectees simultanement`() {
        val positions = mapOf(
            "dpad"     to ButtonPosition(0.32f, 0.65f, 0.60f),  // 25 dp
            "abxy"     to ButtonPosition(0.88f, 0.65f, 0.60f),  // 27 dp
            "btn_back" to ButtonPosition(0.44f, 0.12f, 0.80f),  // 36 dp
        )
        val result = LayoutValidator.validatePositions(positions)
        assertFalse(result.isValid)
        assertEquals("3 violations attendues", 3, result.violations.size)
    }

    @Test
    fun `left_stick et right_stick ne generent jamais de violation`() {
        // Les sticks font 140 dp — toujours au-dessus de 48 dp
        val positions = mapOf(
            "left_stick"  to ButtonPosition(0.13f, 0.65f, 1.0f),
            "right_stick" to ButtonPosition(0.87f, 0.65f, 1.0f)
        )
        val result = LayoutValidator.validatePositions(positions)
        assertTrue(result.isValid)
    }

    @Test
    fun `minSizeMultiplier retourne la valeur correcte pour dpad`() {
        // min = 48 / 42 = 1.14...
        val min = LayoutValidator.minSizeMultiplier("dpad")
        assertTrue("Min dpad ≥ 1.14", min >= 1.14f)
        assertTrue("Min dpad ≤ 1.15", min <= 1.15f)
    }

    @Test
    fun `validate sur profil complet fonctionne`() {
        val pos = mapOf(
            "btn_lt" to ButtonPosition(0.08f, 0.12f, 1.0f),
            "dpad"   to ButtonPosition(0.32f, 0.65f, 0.50f)  // violation
        )
        val result = LayoutValidator.validate(profile(pos))
        assertFalse(result.isValid)
        assertEquals(1, result.violations.size)
    }
}
