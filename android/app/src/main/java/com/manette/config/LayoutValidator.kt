package com.manette.config

import com.manette.data.ControllerProfile

/**
 * Validateur de layout — Kotlin pur, sans dépendance Android (P1-1).
 *
 * Vérifie l'ensemble des contraintes d'accessibilité et d'intégrité sur un layout
 * de profil PHANTOM avant de l'enregistrer.
 *
 * Exécutable en JVM : `./gradlew testDebugUnitTest --tests "com.manette.LayoutValidatorTest"`
 */
object LayoutValidator {

    /**
     * Taille minimale de zone tactile en dp selon Material / Google Accessibility Guidelines.
     * P0-4 : l'éditeur bloque l'enregistrement si la zone tactile effective est < 48 dp.
     *
     * Correspondance `size` multiplicateur → dp effectif :
     *   dpEffective(key, size) = BASE_DP[key] * size
     *
     * Valeurs BASE_DP par type de contrôle (cohérent avec ControllerView.kt) :
     *   btn_lt / btn_rt / btn_lb / btn_rb : 54 dp
     *   btn_back / btn_start              : 46 dp
     *   dpad (direction individuelle)     : 42 dp  ← le plus petit
     *   abxy / btn_a/b/x/y               : 46 dp  ← zone de chaque bouton
     *   left_stick / right_stick          : 140 dp (toujours OK)
     */
    private val baseDpByKey: Map<String, Float> = mapOf(
        "btn_lt"       to 54f,
        "btn_rt"       to 54f,
        "btn_lb"       to 54f,
        "btn_rb"       to 54f,
        "btn_back"     to 48f,
        "btn_start"    to 48f,
        "dpad"         to 42f,   // zone individuelle d'une direction du D-Pad
        "abxy"         to 46f,   // zone individuelle A/B/X/Y
        "btn_a"        to 48f,
        "btn_b"        to 48f,
        "btn_x"        to 48f,
        "btn_y"        to 48f,
        "left_stick"   to 140f,
        "right_stick"  to 140f
    )

    const val MIN_TOUCH_DP = 48f

    /**
     * Résultat de validation.
     * [isValid] = true si toutes les contraintes sont satisfaites.
     * [violations] = liste des violations (clé + message).
     */
    data class ValidationResult(
        val isValid: Boolean,
        val violations: List<Violation> = emptyList()
    )

    data class Violation(
        val controlKey: String,
        val message: String
    )

    /**
     * Valide un profil complet.
     * @return [ValidationResult] avec la liste des violations.
     */
    fun validate(profile: ControllerProfile): ValidationResult {
        return validatePositions(profile.layoutConfig.buttonPositions)
    }

    /**
     * Valide une map de positions.
     * Utilisable directement depuis l'éditeur avec la copie locale de positions.
     */
    fun validatePositions(positions: Map<String, ButtonPosition>): ValidationResult {
        val violations = mutableListOf<Violation>()

        positions.forEach { (key, pos) ->
            val baseDp = baseDpByKey[key] ?: return@forEach  // clé inconnue ignorée
            val effectiveDp = baseDp * pos.size

            if (effectiveDp < MIN_TOUCH_DP) {
                violations.add(
                    Violation(
                        controlKey = key,
                        message = "Zone tactile trop petite : ${effectiveDp.toInt()} dp " +
                            "(minimum ${MIN_TOUCH_DP.toInt()} dp). " +
                            "Augmenter la taille de '${controlKeyLabel(key)}' " +
                            "(multiplicateur min = ${minSizeMultiplier(key)})"
                    )
                )
            }
        }

        return ValidationResult(
            isValid = violations.isEmpty(),
            violations = violations
        )
    }

    /**
     * Multiplicateur `size` minimal pour satisfaire la contrainte 48 dp pour une clé donnée.
     * Retourné avec 2 décimales pour l'affichage dans l'UI.
     */
    fun minSizeMultiplier(key: String): Float {
        val baseDp = baseDpByKey[key] ?: return 1.0f
        return (MIN_TOUCH_DP / baseDp * 100).toInt() / 100f  // arrondi 2 décimales
    }

    /**
     * Taille effective en dp pour une position donnée et sa clé.
     */
    fun effectiveDp(key: String, pos: ButtonPosition): Float {
        val baseDp = baseDpByKey[key] ?: return Float.MAX_VALUE
        return baseDp * pos.size
    }

    private fun controlKeyLabel(key: String): String = when (key) {
        "btn_lt"     -> "LT"
        "btn_rt"     -> "RT"
        "btn_lb"     -> "LB"
        "btn_rb"     -> "RB"
        "btn_back"   -> "BACK"
        "btn_start"  -> "START"
        "dpad"       -> "D-Pad"
        "abxy"       -> "ABXY"
        "btn_a"      -> "A"
        "btn_b"      -> "B"
        "btn_x"      -> "X"
        "btn_y"      -> "Y"
        "left_stick" -> "Stick Gauche"
        "right_stick"-> "Stick Droit"
        else         -> key
    }
}
