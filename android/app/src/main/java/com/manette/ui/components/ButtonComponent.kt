package com.manette.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manette.ui.theme.GamepadButton
import com.manette.ui.theme.GamepadButtonPressed

/**
 * Catégorie haptique d'un bouton — détermine la durée et l'amplitude de vibration.
 * Choix calé sur les conventions des grandes applis de manettes tactiles (PPSSPP, Mantis).
 */
enum class HapticCategory {
    /** ABXY : tap court et net */
    ACTION,
    /** LB / RB : légèrement plus lourd qu'ABXY */
    BUMPER,
    /** LT / RT : gâchette = pression forte */
    TRIGGER,
    /** D-pad : discret, répétable rapidement */
    DPAD,
    /** Start / Back : accessoire, léger */
    CENTER,
    /** Aucun retour haptique */
    NONE
}

/**
 * Déclenche une vibration native du téléphone selon la catégorie du bouton.
 * Utilise [VibrationEffect.createPredefined] (API 29+) avec fallback [VibrationEffect.createOneShot] (API 26+).
 * Aucune permission VIBRATE requise pour les effets courts depuis API 26.
 */
fun performButtonHaptic(context: Context, category: HapticCategory) {
    if (category == HapticCategory.NONE) return

    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (!vibrator.hasVibrator()) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // API 29+ : effets prédéfinis, adaptés au matériel de l'appareil
        val effectId = when (category) {
            HapticCategory.ACTION  -> VibrationEffect.EFFECT_CLICK
            HapticCategory.BUMPER  -> VibrationEffect.EFFECT_CLICK
            HapticCategory.TRIGGER -> VibrationEffect.EFFECT_HEAVY_CLICK
            HapticCategory.DPAD    -> VibrationEffect.EFFECT_TICK
            HapticCategory.CENTER  -> VibrationEffect.EFFECT_TICK
            HapticCategory.NONE    -> return
        }
        vibrator.vibrate(VibrationEffect.createPredefined(effectId))
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // API 26-28 : fallback oneShot avec durée/amplitude fixes
        val (durationMs, amplitude) = when (category) {
            HapticCategory.ACTION  -> Pair(18L, 100)
            HapticCategory.BUMPER  -> Pair(22L, 130)
            HapticCategory.TRIGGER -> Pair(35L, 200)
            HapticCategory.DPAD    -> Pair(12L, 80)
            HapticCategory.CENTER  -> Pair(14L, 70)
            HapticCategory.NONE    -> return
        }
        val amp = if (vibrator.hasAmplitudeControl()) amplitude
                  else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amp))
    }
    // En dessous d'API 26 : pas de VibrationEffect — aucune vibration, pas de crash.
}

@Composable
fun ReactiveGamepadButton(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    shape: Shape = CircleShape,
    defaultColor: Color = GamepadButton,
    pressedColor: Color = GamepadButtonPressed,
    textColor: Color = Color.White,
    fontSize: Int = 18,
    hapticCategory: HapticCategory = HapticCategory.ACTION,
    onPress: () -> Unit = {},
    onRelease: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (isPressed) pressedColor else defaultColor, shape)
            .border(2.dp, if (isPressed) Color.White else Color.White.copy(alpha = 0.25f), shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        // Vibration dans le même événement que la pression (A3 : même image)
                        performButtonHaptic(context, hapticCategory)
                        onPress()
                        val released = tryAwaitRelease()
                        isPressed = false
                        onRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
