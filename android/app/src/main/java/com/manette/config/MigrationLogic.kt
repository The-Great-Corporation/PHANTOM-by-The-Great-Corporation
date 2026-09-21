package com.manette.config

import com.manette.data.ControllerProfile

/**
 * Logique de migration de schéma PHANTOM — Kotlin pur, sans dépendance Android.
 *
 * Extraite de [ProfileManager] pour permettre des tests JVM unitaires (P1-1).
 *
 * ### Règles de migration v1 → v2 (invariantes — ne pas modifier sans accord Carl)
 * 1. Positions pixel (> [PIXEL_THRESHOLD]) → normalisées 0–1 via [LEGACY_REF_W]/[LEGACY_REF_H].
 * 2. Clés legacy `btn_a/b/x/y` → **conservées telles quelles** (pas de centroïde).
 *    La notion de groupe `abxy` est optionnelle et n'écrase jamais les clés individuelles.
 * 3. Clé `left_trigger` / `right_trigger` → `btn_lt` / `btn_rt` si absentes.
 * 4. `schemaVersion` mis à 2.
 *
 * Fix P0-2 : la version précédente calculait le centroïde de btn_a/b/x/y et perdait
 * les positions individuelles. Ce bug est corrigé ici : les clés individuelles sont
 * PRÉSERVÉES et l'alias `abxy` (position du groupe) est calculé séparément mais
 * n'efface plus les 4 clés originales.
 */
class MigrationLogic {

    companion object {
        /** Toute position > ce seuil est considérée en pixels (schéma v1 legacy). */
        const val PIXEL_THRESHOLD = 2.0f

        /** Résolution de référence de l'ancien code. */
        const val LEGACY_REF_W = 1280f
        const val LEGACY_REF_H = 720f

        /** Multiplicateur taille : ancien "pixels" → nouveau multiplicateur (1.0 = taille normale). */
        const val LEGACY_SIZE_REF = 50f
    }

    /**
     * Détecte et migre un profil v1 vers v2.
     * Si [profile.schemaVersion] >= 2, le profil est retourné **sans modification** (`===` identity).
     */
    fun migrateIfNeeded(profile: ControllerProfile): ControllerProfile {
        if (profile.schemaVersion >= 2) return profile

        val rawPositions = profile.layoutConfig.buttonPositions.toMutableMap()

        // ── Étape 1 : normalisation pixel → 0–1 ─────────────────────────────
        val needsPixelNorm = rawPositions.values.any { it.x > PIXEL_THRESHOLD || it.y > PIXEL_THRESHOLD }
        if (needsPixelNorm) {
            rawPositions.replaceAll { _, pos ->
                ButtonPosition(
                    x = if (pos.x > PIXEL_THRESHOLD) (pos.x / LEGACY_REF_W).coerceIn(0f, 1f) else pos.x,
                    y = if (pos.y > PIXEL_THRESHOLD) (pos.y / LEGACY_REF_H).coerceIn(0f, 1f) else pos.y,
                    size = when {
                        pos.size > PIXEL_THRESHOLD -> (pos.size / LEGACY_SIZE_REF).coerceIn(0.5f, 3f)
                        else -> pos.size
                    }
                )
            }
        }

        // ── Étape 2 : clés legacy btn_a/b/x/y — CONSERVER, ne pas moyenner ──
        //
        // Fix P0-2 : l'ancienne implémentation remplaçait les 4 clés par le
        // centroïde `abxy`, perdant les positions individuelles.
        //
        // Nouvelle règle :
        //  • Les clés btn_a, btn_b, btn_x, btn_y sont CONSERVÉES telles quelles.
        //  • Si `abxy` n'existe pas encore, on le crée comme alias de groupe
        //    (centroïde), MAIS sans supprimer les 4 clés individuelles.
        val legacyKeys = listOf("btn_a", "btn_b", "btn_x", "btn_y")
        val legacyPositions = legacyKeys.mapNotNull { rawPositions[it] }
        if (legacyPositions.isNotEmpty() && !rawPositions.containsKey("abxy")) {
            // Alias de groupe (centroïde), lecture seule pour l'éditeur de layout
            val centerX = legacyPositions.map { it.x }.average().toFloat()
            val centerY = legacyPositions.map { it.y }.average().toFloat()
            val avgSize = legacyPositions.map { it.size }.average().toFloat()
            rawPositions["abxy"] = ButtonPosition(centerX, centerY, avgSize)
            // Les clés individuelles sont intentionnellement PRÉSERVÉES (pas de remove)
        }

        // ── Étape 3 : renommage left_trigger/right_trigger → btn_lt/btn_rt ──
        rawPositions["left_trigger"]?.let {
            rawPositions.getOrPut("btn_lt") { it }
            rawPositions.remove("left_trigger")
        }
        rawPositions["right_trigger"]?.let {
            rawPositions.getOrPut("btn_rt") { it }
            rawPositions.remove("right_trigger")
        }

        // ── Étape 4 : compléter les clés manquantes depuis les defaults ──────
        LayoutDefaults.defaultPositions.forEach { (key, default) ->
            rawPositions.getOrPut(key) { default }
        }

        return profile.copy(
            schemaVersion = 2,
            layoutConfig = profile.layoutConfig.copy(buttonPositions = rawPositions),
            updatedAt = System.currentTimeMillis()
        )
    }
}
