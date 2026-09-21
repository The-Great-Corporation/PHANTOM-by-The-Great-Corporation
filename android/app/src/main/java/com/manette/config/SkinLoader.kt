package com.manette.config

import android.content.Context
import android.graphics.Color as AColor
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.manette.ui.components.SkinButtonTheme

/**
 * Structure JSON interne d'un skin (assets/skins/*.json).
 */
private data class SkinJson(
    @SerializedName("skin_id")      val skinId: String = "xbox",
    @SerializedName("display_name") val displayName: String = "Xbox Standard",
    @SerializedName("description")  val description: String = "",
    @SerializedName("schema_version") val schemaVersion: Int = 2,
    @SerializedName("buttons")      val buttons: Map<String, ButtonColorJson> = emptyMap(),
    @SerializedName("dpad_color")   val dpadColor: String = "#353B4E",
    @SerializedName("bumper_color") val bumperColor: String = "#2C3244",
    @SerializedName("center_color") val centerColor: String = "#232838"
)

private data class ButtonColorJson(
    @SerializedName("label") val label: String = "?",
    @SerializedName("color") val color: String = "#FFFFFF"
)

/**
 * Chargeur de skins depuis `assets/skins/`.
 *
 * ### Règle R5
 * Aucune couleur de skin ne doit être codée en dur dans le code Kotlin.
 * Ce loader est la seule source de vérité pour les données visuelles des skins.
 *
 * ### Cache
 * Les skins sont mis en cache en mémoire après le premier chargement.
 * Un appel à [clearCache] permet de forcer un rechargement (pour les tests ou l'éditeur).
 */
object SkinLoader {
    private const val TAG = "SkinLoader"
    private const val SKIN_DIR = "skins"

    private val gson = Gson()
    private val cache = mutableMapOf<String, SkinButtonTheme>()

    /** Métadonnées légères (id + nom + description) sans charger le thème complet. */
    data class SkinMeta(val skinId: String, val displayName: String, val description: String)

    /**
     * Retourne le [SkinButtonTheme] pour le skin demandé.
     * Cherche d'abord dans le cache, puis lit `assets/skins/<skinId>.json`.
     * Si l'asset est absent ou mal formé, retourne le skin Xbox par défaut hardcodé
     * comme filet de sécurité (R1 — zéro régression).
     */
    fun loadSkin(context: Context, skinId: String): SkinButtonTheme {
        val key = skinId.lowercase().trim()
        cache[key]?.let { return it }

        return try {
            val json = context.assets.open("$SKIN_DIR/$key.json")
                .bufferedReader().use { it.readText() }
            val parsed = gson.fromJson(json, SkinJson::class.java)
            parsed.toTheme().also { cache[key] = it }
        } catch (e: Exception) {
            Log.w(TAG, "Skin '$skinId' not found in assets — using Xbox fallback: ${e.message}")
            xboxFallback().also { cache[key] = it }
        }
    }

    /**
     * Liste les skins disponibles (fichiers JSON présents dans `assets/skins/`).
     * Retourne une liste de [SkinMeta] triée par skin_id.
     */
    fun listSkins(context: Context): List<SkinMeta> {
        return try {
            val files = context.assets.list(SKIN_DIR) ?: return emptyList()
            files.filter { it.endsWith(".json") }.mapNotNull { filename ->
                try {
                    val json = context.assets.open("$SKIN_DIR/$filename")
                        .bufferedReader().use { it.readText() }
                    val parsed = gson.fromJson(json, SkinJson::class.java)
                    SkinMeta(parsed.skinId, parsed.displayName, parsed.description)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read skin metadata from $filename: ${e.message}")
                    null
                }
            }.sortedBy { it.skinId }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list skins: ${e.message}")
            emptyList()
        }
    }

    /** Force le rechargement du cache (utile pour les tests). */
    fun clearCache() = cache.clear()

    // ── Conversion JSON → Compose ──────────────────────────────────────────────

    private fun SkinJson.toTheme(): SkinButtonTheme {
        fun String.toComposeColor(): Color =
            try { Color(AColor.parseColor(if (length == 9) "#${substring(7)}${substring(1, 7)}" else this)) }
            catch (e: Exception) { Color.White }

        // Notation RRGGBBAA → AARRGGBB pour Android Color.parseColor
        fun String.toComposeColorAware(): Color {
            val hex = this.trimStart('#')
            return try {
                when (hex.length) {
                    6 -> Color(AColor.parseColor("#$hex"))
                    8 -> {
                        // RRGGBBAA → Android attend AARRGGBB
                        val rr = hex.substring(0, 2)
                        val gg = hex.substring(2, 4)
                        val bb = hex.substring(4, 6)
                        val aa = hex.substring(6, 8)
                        Color(AColor.parseColor("#$aa$rr$gg$bb"))
                    }
                    else -> Color.White
                }
            } catch (e: Exception) { Color.White }
        }

        val a = buttons["a"] ?: ButtonColorJson("A", "#2ECC71")
        val b = buttons["b"] ?: ButtonColorJson("B", "#E74C3C")
        val x = buttons["x"] ?: ButtonColorJson("X", "#3498DB")
        val y = buttons["y"] ?: ButtonColorJson("Y", "#F1C40F")

        return SkinButtonTheme(
            aLabel = a.label, aColor = a.color.toComposeColorAware(),
            bLabel = b.label, bColor = b.color.toComposeColorAware(),
            xLabel = x.label, xColor = x.color.toComposeColorAware(),
            yLabel = y.label, yColor = y.color.toComposeColorAware(),
            dpadColor   = dpadColor.toComposeColorAware(),
            bumperColor = bumperColor.toComposeColorAware(),
            centerColor = centerColor.toComposeColorAware()
        )
    }

    /** Fallback Xbox codé en dur (unique endroit autorisé, filet de sécurité R1). */
    private fun xboxFallback() = SkinButtonTheme(
        aLabel = "A", aColor = Color(0xFF2ECC71),
        bLabel = "B", bColor = Color(0xFFE74C3C),
        xLabel = "X", xColor = Color(0xFF3498DB),
        yLabel = "Y", yColor = Color(0xFFF1C40F)
    )
}
