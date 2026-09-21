package com.manette.config

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.manette.data.ControllerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class ProfileManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val profilesDir = File(context.filesDir, "profiles")

    companion object {
        private const val TAG = "ProfileManager"
        private const val DEFAULT_PROFILE_NAME = "default_profile.json"

        /** Toute position au-dessus de ce seuil est considérée en pixels (schéma v1 legacy). */
        private const val PIXEL_THRESHOLD = 2.0f

        /** Résolution de référence utilisée par l'ancien code pour calculer les positions pixel. */
        private const val LEGACY_REF_W = 1280f
        private const val LEGACY_REF_H = 720f
    }

    init {
        if (!profilesDir.exists()) {
            profilesDir.mkdirs()
        }
    }

    // ── Opérations CRUD ───────────────────────────────────────────────────────

    suspend fun saveProfile(profile: ControllerProfile, filename: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = File(profilesDir, filename)
                FileWriter(file).use { gson.toJson(profile, it) }
                Log.d(TAG, "Profile saved to $filename")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save profile: ${e.message}")
                false
            }
        }

    suspend fun loadProfile(filename: String): ControllerProfile? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(profilesDir, filename)
                if (!file.exists()) {
                    Log.w(TAG, "Profile file not found: $filename")
                    return@withContext null
                }
                val raw = FileReader(file).use { gson.fromJson(it, ControllerProfile::class.java) }
                migrateIfNeeded(raw).also {
                    if (it.schemaVersion != raw.schemaVersion) {
                        // Réécrire si la migration a modifié le profil
                        FileWriter(File(profilesDir, filename)).use { w -> gson.toJson(it, w) }
                        Log.i(TAG, "Profile '$filename' auto-migrated to schemaVersion=${it.schemaVersion}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profile: ${e.message}")
                null
            }
        }

    suspend fun deleteProfile(filename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(profilesDir, filename)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Profile deleted: $filename")
                true
            } else {
                Log.w(TAG, "Profile file not found: $filename")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete profile: ${e.message}")
            false
        }
    }

    suspend fun listProfiles(): List<String> = withContext(Dispatchers.IO) {
        try {
            profilesDir.listFiles()?.map { it.name }?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list profiles: ${e.message}")
            emptyList()
        }
    }

    // ── Export / Import (Fichiers & Flux .phantom) ───────────────────────────

    suspend fun exportProfile(profile: ControllerProfile, destinationPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                FileWriter(File(destinationPath)).use { gson.toJson(profile, it) }
                Log.d(TAG, "Profile exported to $destinationPath")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export profile: ${e.message}")
                false
            }
        }

    suspend fun exportProfileToStream(profile: ControllerProfile, outputStream: java.io.OutputStream): Boolean =
        withContext(Dispatchers.IO) {
            try {
                java.io.OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    gson.toJson(profile, writer)
                }
                Log.d(TAG, "Profile exported to stream successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export profile to stream: ${e.message}")
                false
            }
        }

    suspend fun importProfile(sourcePath: String): ControllerProfile? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(sourcePath)
                if (!file.exists()) {
                    Log.w(TAG, "Profile file not found: $sourcePath")
                    return@withContext null
                }
                val raw = FileReader(file).use { gson.fromJson(it, ControllerProfile::class.java) }
                migrateIfNeeded(raw).also {
                    Log.d(TAG, "Profile imported from $sourcePath (schemaVersion=${it.schemaVersion})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import profile: ${e.message}")
                null
            }
        }

    suspend fun importProfileFromStream(inputStream: java.io.InputStream): ControllerProfile? =
        withContext(Dispatchers.IO) {
            try {
                val raw = java.io.InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                    gson.fromJson(reader, ControllerProfile::class.java)
                }
                migrateIfNeeded(raw).also {
                    Log.d(TAG, "Profile imported from stream (name=${it.name}, schemaVersion=${it.schemaVersion})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import profile from stream: ${e.message}")
                null
            }
        }

    suspend fun duplicateProfile(sourceFilename: String, newName: String, targetFilename: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val src = loadProfile(sourceFilename) ?: return@withContext false
                val duplicated = src.copy(name = newName, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                saveProfile(duplicated, targetFilename)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to duplicate profile: ${e.message}")
                false
            }
        }

    suspend fun renameProfile(filename: String, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val profile = loadProfile(filename) ?: return@withContext false
                val renamed = profile.copy(name = newName, updatedAt = System.currentTimeMillis())
                saveProfile(renamed, filename)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename profile: ${e.message}")
                false
            }
        }

    // ── Profil par défaut ─────────────────────────────────────────────────────

    suspend fun saveDefaultProfile(profile: ControllerProfile): Boolean =
        saveProfile(profile, DEFAULT_PROFILE_NAME)

    suspend fun loadDefaultProfile(): ControllerProfile? =
        loadProfile(DEFAULT_PROFILE_NAME)

    /**
     * Charge le profil par défaut s'il existe, sinon le crée depuis le preset JSON
     * `assets/presets/manette.json`. En cas d'échec du preset, repli sur les constantes
     * de [LayoutDefaults] (filet de sécurité — R1 zéro régression).
     */
    suspend fun createDefaultProfileIfNotExists(): ControllerProfile {
        val existing = loadDefaultProfile()
        if (existing != null) return existing

        // Source de vérité : preset JSON versionné
        val fromPreset = PresetLoader.loadDefaultPreset(context)
        if (fromPreset != null) {
            Log.d(TAG, "Default profile built from preset 'manette.json'")
            saveDefaultProfile(fromPreset)
            return fromPreset
        }

        // Filet de sécurité : constantes statiques (en cas d'asset manquant)
        Log.w(TAG, "Preset file not found — falling back to LayoutDefaults constants")
        val fallback = buildFallbackProfile()
        saveDefaultProfile(fallback)
        return fallback
    }

    // ── Migration schéma ──────────────────────────────────────────────────────

    /**
     * Détecte et migre un profil v1 (schemaVersion absent = 0, ou toute valeur < 2) vers v2.
     *
     * ### Règles de migration v1 → v2
     * 1. Positions pixel (> [PIXEL_THRESHOLD]) → normalisées 0–1 via [LEGACY_REF_W]/[LEGACY_REF_H].
     * 2. Clés legacy `btn_a/b/x/y` → regroupées sous `abxy` (centroïde moyen).
     * 3. Clé `left_trigger` / `right_trigger` → `btn_lt` / `btn_rt` si absentes.
     * 4. `schemaVersion` mis à 2.
     *
     * Si le profil est déjà à `schemaVersion` >= 2, il est retourné intact.
     */
    fun migrateIfNeeded(profile: ControllerProfile): ControllerProfile {
        if (profile.schemaVersion >= 2) return profile

        Log.i(TAG, "Migrating profile '${profile.name}' from schemaVersion=${profile.schemaVersion} to 2")

        val rawPositions = profile.layoutConfig.buttonPositions.toMutableMap()

        // ── Étape 1 : normalisation des positions pixel ────────────────────────
        val needsPixelNorm = rawPositions.values.any { it.x > PIXEL_THRESHOLD || it.y > PIXEL_THRESHOLD }
        if (needsPixelNorm) {
            Log.d(TAG, "Normalizing pixel positions to 0–1 range")
            rawPositions.replaceAll { _, pos ->
                ButtonPosition(
                    x = if (pos.x > PIXEL_THRESHOLD) (pos.x / LEGACY_REF_W).coerceIn(0f, 1f) else pos.x,
                    y = if (pos.y > PIXEL_THRESHOLD) (pos.y / LEGACY_REF_H).coerceIn(0f, 1f) else pos.y,
                    size = when {
                        pos.size > PIXEL_THRESHOLD -> (pos.size / 50f).coerceIn(0.5f, 3f)  // pixels → multiplicateur
                        else -> pos.size
                    }
                )
            }
        }

        // ── Étape 2 : clés legacy btn_a/b/x/y → abxy ──────────────────────────
        if (!rawPositions.containsKey("abxy")) {
            val legacyKeys = listOf("btn_a", "btn_b", "btn_x", "btn_y")
            val legacyPositions = legacyKeys.mapNotNull { rawPositions[it] }
            if (legacyPositions.isNotEmpty()) {
                val centerX = legacyPositions.map { it.x }.average().toFloat()
                val centerY = legacyPositions.map { it.y }.average().toFloat()
                val avgSize = legacyPositions.map { it.size }.average().toFloat()
                rawPositions["abxy"] = ButtonPosition(centerX, centerY, avgSize)
                legacyKeys.forEach { rawPositions.remove(it) }
                Log.d(TAG, "Migrated legacy ABXY keys → abxy centroid ($centerX, $centerY)")
            }
        }

        // ── Étape 3 : renommage left_trigger/right_trigger → btn_lt/btn_rt ────
        rawPositions["left_trigger"]?.let { rawPositions.getOrPut("btn_lt") { it }; rawPositions.remove("left_trigger") }
        rawPositions["right_trigger"]?.let { rawPositions.getOrPut("btn_rt") { it }; rawPositions.remove("right_trigger") }

        // ── Étape 4 : compléter les clés manquantes depuis les defaults ─────────
        LayoutDefaults.defaultPositions.forEach { (key, default) ->
            rawPositions.getOrPut(key) { default }
        }

        return profile.copy(
            schemaVersion = 2,
            layoutConfig = profile.layoutConfig.copy(buttonPositions = rawPositions),
            updatedAt = System.currentTimeMillis()
        )
    }

    // ── Filet de sécurité ─────────────────────────────────────────────────────

    private fun buildFallbackProfile(): ControllerProfile = ControllerProfile(
        name = "Default",
        schemaVersion = 2,
        buttonMappings = mapOf(
            "a" to "button_a", "b" to "button_b",
            "x" to "button_x", "y" to "button_y",
            "left_bumper" to "button_l1", "right_bumper" to "button_r1",
            "back" to "button_select", "start" to "button_start",
            "left_thumb" to "button_l3", "right_thumb" to "button_r3",
            "dpad_up" to "dpad_up", "dpad_down" to "dpad_down",
            "dpad_left" to "dpad_left", "dpad_right" to "dpad_right"
        ),
        joystickSettings = mapOf(
            "left" to JoystickConfig(),
            "right" to JoystickConfig()
        ),
        sensitivitySettings = SensitivityConfig(),
        deadzoneSettings = DeadzoneConfig(),
        layoutConfig = LayoutConfig(
            buttonPositions = LayoutDefaults.defaultPositions,
            skin = "xbox"
        )
    )
}
