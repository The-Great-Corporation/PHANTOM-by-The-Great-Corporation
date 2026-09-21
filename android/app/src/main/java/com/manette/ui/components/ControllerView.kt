package com.manette.ui.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.manette.config.ButtonPosition
import com.manette.config.LayoutDefaults
import com.manette.config.SkinLoader
import com.manette.ui.theme.*

data class SkinButtonTheme(
    val aLabel: String, val aColor: Color,
    val bLabel: String, val bColor: Color,
    val xLabel: String, val xColor: Color,
    val yLabel: String, val yColor: Color,
    val dpadColor: Color = Color(0xFF353B4E),
    val bumperColor: Color = Color(0xFF2C3244),
    val centerColor: Color = Color(0xFF232838)
)

/**
 * Charge le thème de skin depuis [SkinLoader] (assets/skins/<skinId>.json).
 * Conforme à la règle R5 : aucune couleur n'est codée en dur ici.
 */
fun getSkinTheme(context: Context, skin: String): SkinButtonTheme =
    SkinLoader.loadSkin(context, skin)

/**
 * Surcharge sans Context pour la rétrocompatibilité des appels hors Composable.
 * Utilise le fallback statique Xbox — à éviter dans le code UI (utiliser la version Context).
 */
@Deprecated("Préférer getSkinTheme(context, skin) qui lit depuis les assets")
fun getSkinTheme(skin: String): SkinButtonTheme {
    return when (skin.lowercase()) {
        "playstation", "ps" -> SkinButtonTheme(
            aLabel = "✕", aColor = Color(0xFF2980B9),
            bLabel = "◯", bColor = Color(0xFFE74C3C),
            xLabel = "▢", xColor = Color(0xFFE91E63),
            yLabel = "△", yColor = Color(0xFF2ECC71)
        )
        "nintendo", "switch" -> SkinButtonTheme(
            aLabel = "B", aColor = Color(0xFFE74C3C),
            bLabel = "A", bColor = Color(0xFF2ECC71),
            xLabel = "Y", xColor = Color(0xFFF1C40F),
            yLabel = "X", yColor = Color(0xFF3498DB)
        )
        "cyberpunk", "neon" -> SkinButtonTheme(
            aLabel = "A", aColor = Color(0xFF00E5FF),
            bLabel = "B", bColor = Color(0xFFFF0055),
            xLabel = "X", xColor = Color(0xFF00FF66),
            yLabel = "Y", yColor = Color(0xFFFFCC00),
            dpadColor = Color(0xFF1E2638)
        )
        "ghost", "minimal" -> SkinButtonTheme(
            aLabel = "A", aColor = Color.White.copy(alpha = 0.2f),
            bLabel = "B", bColor = Color.White.copy(alpha = 0.2f),
            xLabel = "X", xColor = Color.White.copy(alpha = 0.2f),
            yLabel = "Y", yColor = Color.White.copy(alpha = 0.2f),
            dpadColor = Color.White.copy(alpha = 0.15f),
            bumperColor = Color.White.copy(alpha = 0.2f),
            centerColor = Color.White.copy(alpha = 0.15f)
        )
        else -> SkinButtonTheme(
            aLabel = "A", aColor = Color(0xFF2ECC71),
            bLabel = "B", bColor = Color(0xFFE74C3C),
            xLabel = "X", xColor = Color(0xFF3498DB),
            yLabel = "Y", yColor = Color(0xFFF1C40F)
        )
    }
}

@Composable
fun ControllerView(
    modifier: Modifier = Modifier,
    skin: String = "xbox",
    positions: Map<String, ButtonPosition> = emptyMap(),
    floatingSticks: Boolean = true,
    onButtonPress: (String, Boolean) -> Unit,
    onJoystickMove: (String, Float, Float) -> Unit
) {
    val context = LocalContext.current
    val theme = getSkinTheme(context, skin)
    val effectivePositions = remember(positions) {
        LayoutDefaults.getEffectivePositions(positions)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenW = maxWidth.value
        val screenH = maxHeight.value

        // ── 0. STICK FLOTTANT GAUCHE (en dessous de tout — couche de fond) ────────
        //
        // Fix P0-1 : FloatingJoystickZone est composé EN PREMIER pour qu'il soit
        // en dessous dans l'ordre z (Compose donne la priorité tactile au dernier
        // frère). Les boutons LT, LB et Back composés APRÈS captent les events dans
        // leur zone sans être bloqués par la zone flottante.
        if (floatingSticks) {
            FloatingJoystickZone(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f),
                onMove = { x, y -> onJoystickMove("left", x, y) }
            )
        }

        // ── 1. GÂCHETTE GAUCHE (LT) ───────────────────────────────────────────
        effectivePositions["btn_lt"]?.let { pos ->
            val size = (54f * pos.size).dp
            Box(
                modifier = Modifier
                    .offset(
                        x = (screenW * pos.x - size.value / 2f).dp,
                        y = (screenH * pos.y - size.value / 2f).dp
                    )
                    .size(size)
            ) {
                ReactiveGamepadButton(
                    label = "LT",
                    size = size,
                    shape = RoundedCornerShape(12.dp),
                    defaultColor = theme.bumperColor,
                    fontSize = (14 * pos.size).toInt().coerceAtLeast(9),
                    hapticCategory = HapticCategory.TRIGGER,
                    onPress = { onButtonPress("left_trigger", true) },
                    onRelease = { onButtonPress("left_trigger", false) }
                )
            }
        }

        // ── 2. BUMPER GAUCHE (LB) ─────────────────────────────────────────────
        effectivePositions["btn_lb"]?.let { pos ->
            val size = (54f * pos.size).dp
            Box(
                modifier = Modifier
                    .offset(
                        x = (screenW * pos.x - size.value / 2f).dp,
                        y = (screenH * pos.y - size.value / 2f).dp
                    )
                    .size(size)
            ) {
                ReactiveGamepadButton(
                    label = "LB",
                    size = size,
                    shape = RoundedCornerShape(12.dp),
                    defaultColor = theme.bumperColor,
                    fontSize = (14 * pos.size).toInt().coerceAtLeast(9),
                    hapticCategory = HapticCategory.BUMPER,
                    onPress = { onButtonPress("left_bumper", true) },
                    onRelease = { onButtonPress("left_bumper", false) }
                )
            }
        }

        // ── 3. TOUCHE BACK / SELECT ───────────────────────────────────────────
        effectivePositions["btn_back"]?.let { pos ->
            val size = (46f * pos.size).dp
            Box(
                modifier = Modifier
                    .offset(
                        x = (screenW * pos.x - size.value / 2f).dp,
                        y = (screenH * pos.y - size.value / 2f).dp
                    )
                    .size(size)
            ) {
                ReactiveGamepadButton(
                    label = "BACK",
                    size = size,
                    shape = RoundedCornerShape(10.dp),
                    defaultColor = theme.centerColor,
                    fontSize = (10 * pos.size).toInt().coerceAtLeast(8),
                    hapticCategory = HapticCategory.CENTER,
                    onPress = { onButtonPress("back", true) },
                    onRelease = { onButtonPress("back", false) }
                )
            }
        }

        // ── 4. TOUCHE START ───────────────────────────────────────────────────
        effectivePositions["btn_start"]?.let { pos ->
            val size = (46f * pos.size).dp
            Box(
                modifier = Modifier
                    .offset(
                        x = (screenW * pos.x - size.value / 2f).dp,
                        y = (screenH * pos.y - size.value / 2f).dp
                    )
                    .size(size)
            ) {
                ReactiveGamepadButton(
                    label = "START",
                    size = size,
                    shape = RoundedCornerShape(10.dp),
                    defaultColor = theme.centerColor,
                    fontSize = (10 * pos.size).toInt().coerceAtLeast(8),
                    hapticCategory = HapticCategory.CENTER,
                    onPress = { onButtonPress("start", true) },
                    onRelease = { onButtonPress("start", false) }
                )
            }
        }

        // ── 5. BUMPER DROIT (RB) ──────────────────────────────────────────────
        effectivePositions["btn_rb"]?.let { pos ->
            val size = (54f * pos.size).dp
            Box(
                modifier = Modifier
                    .offset(
                        x = (screenW * pos.x - size.value / 2f).dp,
                        y = (screenH * pos.y - size.value / 2f).dp
                    )
                    .size(size)
            ) {
                ReactiveGamepadButton(
                    label = "RB",
                    size = size,
                    shape = RoundedCornerShape(12.dp),
                    defaultColor = theme.bumperColor,
                    fontSize = (14 * pos.size).toInt().coerceAtLeast(9),
                    hapticCategory = HapticCategory.BUMPER,
                    onPress = { onButtonPress("right_bumper", true) },
                    onRelease = { onButtonPress("right_bumper", false) }
                )
            }
        }

        // ── 6. GÂCHETTE DROITE (RT) ───────────────────────────────────────────
        effectivePositions["btn_rt"]?.let { pos ->
            val size = (54f * pos.size).dp
            Box(
                modifier = Modifier
                    .offset(
                        x = (screenW * pos.x - size.value / 2f).dp,
                        y = (screenH * pos.y - size.value / 2f).dp
                    )
                    .size(size)
            ) {
                ReactiveGamepadButton(
                    label = "RT",
                    size = size,
                    shape = RoundedCornerShape(12.dp),
                    defaultColor = theme.bumperColor,
                    fontSize = (14 * pos.size).toInt().coerceAtLeast(9),
                    hapticCategory = HapticCategory.TRIGGER,
                    onPress = { onButtonPress("right_trigger", true) },
                    onRelease = { onButtonPress("right_trigger", false) }
                )
            }
        }

        // ── 7. STICK ANALOGIQUE GAUCHE (mode fixe uniquement) ─────────────────
        if (!floatingSticks) {
            // Mode fixe : respecte la position de l'éditeur de layout
            effectivePositions["left_stick"]?.let { pos ->
                val size = (140f * pos.size).dp
                Box(
                    modifier = Modifier
                        .offset(
                            x = (screenW * pos.x - size.value / 2f).dp,
                            y = (screenH * pos.y - size.value / 2f).dp
                        )
                        .size(size)
                ) {
                    JoystickComponent(
                        size = size.value.toInt(),
                        onMove = { x, y -> onJoystickMove("left", x, y) }
                    )
                }
            }
        }

        // ── 8. CROIX DIRECTIONNELLE (D-PAD) ───────────────────────────────────
        effectivePositions["dpad"]?.let { pos ->
            val size = (130f * pos.size).dp
            val btnSize = (42f * pos.size).dp
            Box(
                modifier = Modifier
                    .offset(
                        x = (screenW * pos.x - size.value / 2f).dp,
                        y = (screenH * pos.y - size.value / 2f).dp
                    )
                    .size(size),
                contentAlignment = Alignment.Center
            ) {
                ReactiveGamepadButton(
                    label = "▲",
                    modifier = Modifier.align(Alignment.TopCenter),
                    size = btnSize,
                    shape = RoundedCornerShape(8.dp),
                    defaultColor = theme.dpadColor,
                    hapticCategory = HapticCategory.DPAD,
                    onPress = { onButtonPress("dpad_up", true) },
                    onRelease = { onButtonPress("dpad_up", false) }
                )
                ReactiveGamepadButton(
                    label = "▼",
                    modifier = Modifier.align(Alignment.BottomCenter),
                    size = btnSize,
                    shape = RoundedCornerShape(8.dp),
                    defaultColor = theme.dpadColor,
                    hapticCategory = HapticCategory.DPAD,
                    onPress = { onButtonPress("dpad_down", true) },
                    onRelease = { onButtonPress("dpad_down", false) }
                )
                ReactiveGamepadButton(
                    label = "◀",
                    modifier = Modifier.align(Alignment.CenterStart),
                    size = btnSize,
                    shape = RoundedCornerShape(8.dp),
                    defaultColor = theme.dpadColor,
                    hapticCategory = HapticCategory.DPAD,
                    onPress = { onButtonPress("dpad_left", true) },
                    onRelease = { onButtonPress("dpad_left", false) }
                )
                ReactiveGamepadButton(
                    label = "▶",
                    modifier = Modifier.align(Alignment.CenterEnd),
                    size = btnSize,
                    shape = RoundedCornerShape(8.dp),
                    defaultColor = theme.dpadColor,
                    hapticCategory = HapticCategory.DPAD,
                    onPress = { onButtonPress("dpad_right", true) },
                    onRelease = { onButtonPress("dpad_right", false) }
                )
            }
        }

        // ── 9. STICK ANALOGIQUE DROIT ─────────────────────────────────────────
        effectivePositions["right_stick"]?.let { pos ->
            val size = (140f * pos.size).dp
            Box(
                modifier = Modifier
                    .offset(
                        x = (screenW * pos.x - size.value / 2f).dp,
                        y = (screenH * pos.y - size.value / 2f).dp
                    )
                    .size(size)
            ) {
                JoystickComponent(
                    size = size.value.toInt(),
                    onMove = { x, y -> onJoystickMove("right", x, y) }
                )
            }
        }

        // ── 10. TOUCHES D'ACTION (ABXY) EN GROUPE UNIFIÉ ──────────────────────
        effectivePositions["abxy"]?.let { pos ->
            val clusterSize = (130f * pos.size).dp
            val btnSize = (46f * pos.size).dp
            Box(
                modifier = Modifier
                    .offset(
                        x = (screenW * pos.x - clusterSize.value / 2f).dp,
                        y = (screenH * pos.y - clusterSize.value / 2f).dp
                    )
                    .size(clusterSize),
                contentAlignment = Alignment.Center
            ) {
                // Touche Y (Haut)
                ReactiveGamepadButton(
                    label = theme.yLabel,
                    modifier = Modifier.align(Alignment.TopCenter),
                    size = btnSize,
                    shape = CircleShape,
                    defaultColor = theme.yColor,
                    fontSize = (16 * pos.size).toInt().coerceAtLeast(10),
                    onPress = { onButtonPress("y", true) },
                    onRelease = { onButtonPress("y", false) }
                )
                // Touche A (Bas)
                ReactiveGamepadButton(
                    label = theme.aLabel,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    size = btnSize,
                    shape = CircleShape,
                    defaultColor = theme.aColor,
                    fontSize = (16 * pos.size).toInt().coerceAtLeast(10),
                    onPress = { onButtonPress("a", true) },
                    onRelease = { onButtonPress("a", false) }
                )
                // Touche X (Gauche)
                ReactiveGamepadButton(
                    label = theme.xLabel,
                    modifier = Modifier.align(Alignment.CenterStart),
                    size = btnSize,
                    shape = CircleShape,
                    defaultColor = theme.xColor,
                    fontSize = (16 * pos.size).toInt().coerceAtLeast(10),
                    onPress = { onButtonPress("x", true) },
                    onRelease = { onButtonPress("x", false) }
                )
                // Touche B (Droite)
                ReactiveGamepadButton(
                    label = theme.bLabel,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    size = btnSize,
                    shape = CircleShape,
                    defaultColor = theme.bColor,
                    fontSize = (16 * pos.size).toInt().coerceAtLeast(10),
                    onPress = { onButtonPress("b", true) },
                    onRelease = { onButtonPress("b", false) }
                )
            }
        }
    }
}
