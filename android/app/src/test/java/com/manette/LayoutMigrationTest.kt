package com.manette

import com.manette.config.*
import com.manette.data.ControllerProfile
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests JVM pour [ProfileManager.migrateIfNeeded].
 *
 * Ces tests s'exécutent en JVM pure (pas besoin d'Android) :
 *   ./gradlew testDebugUnitTest --tests "com.manette.LayoutMigrationTest"
 *
 * Critère A9 (TRACABILITE_CRITERES.md) :
 *  Les positions individuelles A/B/X/Y doivent être conservées telles quelles.
 */
class LayoutMigrationTest {

    // Instancie la logique de migration sans contexte Android
    private val migrator = MigrationLogic()

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun legacyProfile(positions: Map<String, ButtonPosition>): ControllerProfile =
        ControllerProfile(
            name = "test",
            schemaVersion = 0,
            layoutConfig = LayoutConfig(buttonPositions = positions)
        )

    // ── P0-2 : Conservation des positions individuelles ABXY ──────────────────

    /**
     * Régression détectée : la migration v1→v2 calculait le centroïde de btn_a/b/x/y
     * et perdait les positions individuelles.
     * Ce test ÉCHOUE sur le code buggé et PASSE après correction.
     */
    @Test
    fun `P0-2 migration conserve les quatre positions ABXY sans les moyenner`() {
        val posA = ButtonPosition(x = 0.85f, y = 0.80f, size = 1.0f)
        val posB = ButtonPosition(x = 0.90f, y = 0.70f, size = 1.0f)
        val posX = ButtonPosition(x = 0.80f, y = 0.70f, size = 1.0f)
        val posY = ButtonPosition(x = 0.85f, y = 0.60f, size = 1.0f)

        val input = legacyProfile(
            mapOf(
                "btn_a" to posA,
                "btn_b" to posB,
                "btn_x" to posX,
                "btn_y" to posY
            )
        )

        val result = migrator.migrateIfNeeded(input)
        val positions = result.layoutConfig.buttonPositions

        // Les 4 éléments doivent exister individuellement
        assertNotNull("btn_a doit être présent après migration", positions["btn_a"])
        assertNotNull("btn_b doit être présent après migration", positions["btn_b"])
        assertNotNull("btn_x doit être présent après migration", positions["btn_x"])
        assertNotNull("btn_y doit être présent après migration", positions["btn_y"])

        // Positions inchangées (pas de centroïde)
        assertEquals("Position de A préservée", posA.x, positions["btn_a"]!!.x, 0.001f)
        assertEquals("Position de A préservée", posA.y, positions["btn_a"]!!.y, 0.001f)
        assertEquals("Position de B préservée", posB.x, positions["btn_b"]!!.x, 0.001f)
        assertEquals("Position de X préservée", posX.x, positions["btn_x"]!!.x, 0.001f)
        assertEquals("Position de Y préservée", posY.x, positions["btn_y"]!!.x, 0.001f)

        // abxy peut exister comme alias de groupe (optionnel), mais ne doit PAS
        // être le SEUL élément remplaçant les 4 positions individuelles
        val hasIndividual = positions.containsKey("btn_a")
        val hasCentroidOnly = !positions.containsKey("btn_a") && positions.containsKey("abxy")
        assertFalse("Ne doit pas remplacer A/B/X/Y par centroïde", hasCentroidOnly)
        assertTrue("Les clés individuelles sont présentes", hasIndividual)
    }

    @Test
    fun `P0-2 les anciennes clés legacy btn_a b x y ne sont pas supprimées`() {
        val input = legacyProfile(
            mapOf(
                "btn_a" to ButtonPosition(0.88f, 0.75f, 1.0f),
                "btn_b" to ButtonPosition(0.93f, 0.65f, 1.0f),
                "btn_x" to ButtonPosition(0.83f, 0.65f, 1.0f),
                "btn_y" to ButtonPosition(0.88f, 0.55f, 1.0f),
            )
        )
        val result = migrator.migrateIfNeeded(input)
        listOf("btn_a", "btn_b", "btn_x", "btn_y").forEach { key ->
            assertNotNull("Clé '$key' doit survivre à la migration", result.layoutConfig.buttonPositions[key])
        }
    }

    @Test
    fun `P0-2 profil v2 n est pas retouche`() {
        val alreadyV2 = ControllerProfile(
            name = "v2_profile",
            schemaVersion = 2,
            layoutConfig = LayoutConfig(
                buttonPositions = mapOf(
                    "btn_a" to ButtonPosition(0.88f, 0.75f, 1.0f),
                    "abxy" to ButtonPosition(0.88f, 0.65f, 1.0f)
                )
            )
        )
        val result = migrator.migrateIfNeeded(alreadyV2)
        assertSame("Profil v2 retourné sans modification", alreadyV2, result)
    }

    // ── Migration v1→v2 : normalisation pixel ──────────────────────────────────

    @Test
    fun `migration normalise les positions pixel legacy vers 0-1`() {
        val input = legacyProfile(
            mapOf(
                "btn_lt" to ButtonPosition(x = 102f, y = 86f, size = 50f),
                "btn_lb" to ButtonPosition(x = 230f, y = 86f, size = 50f)
            )
        )
        val result = migrator.migrateIfNeeded(input)
        val lt = result.layoutConfig.buttonPositions["btn_lt"]!!
        assertTrue("x normalisé < 1.0", lt.x <= 1.0f)
        assertTrue("y normalisé < 1.0", lt.y <= 1.0f)
        assertEquals("schemaVersion=2 après migration", 2, result.schemaVersion)
    }

    @Test
    fun `migration renomme left_trigger en btn_lt`() {
        val input = legacyProfile(
            mapOf("left_trigger" to ButtonPosition(0.08f, 0.12f, 1.0f))
        )
        val result = migrator.migrateIfNeeded(input)
        val positions = result.layoutConfig.buttonPositions
        assertNotNull("btn_lt doit exister", positions["btn_lt"])
        assertNull("left_trigger doit être supprimé", positions["left_trigger"])
    }
}
