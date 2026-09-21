package com.manette.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.manette.ui.theme.GamepadButton
import com.manette.ui.theme.GamepadButtonPressed
import kotlin.math.*

// ─── Joystick fixe (comportement original conservé) ──────────────────────────

/**
 * Joystick fixe positionné par l'éditeur de layout.
 * Le knob saute à la position initiale du toucher (dans sa zone),
 * puis suit le glissement. À l'origine du fichier — comportement inchangé.
 */
@Composable
fun JoystickComponent(
    modifier: Modifier = Modifier,
    size: Int = 130,
    onMove: (Float, Float) -> Unit
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val maxRadiusPx = with(density) { (size / 2 - 25).dp.toPx() }

    fun updateFromOffset(newOffset: Offset) {
        val distance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
        thumbOffset = if (distance > maxRadiusPx) {
            val angle = atan2(newOffset.y, newOffset.x)
            Offset(cos(angle) * maxRadiusPx, sin(angle) * maxRadiusPx)
        } else {
            newOffset
        }

        val normX = (thumbOffset.x / maxRadiusPx).coerceIn(-1f, 1f)
        // Invert Y so that pushing stick forward gives positive Y
        val normY = -(thumbOffset.y / maxRadiusPx).coerceIn(-1f, 1f)
        onMove(normX, normY)
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .pointerInput(Unit) {
                val centerPx = size.dp.toPx() / 2f
                detectDragGestures(
                    onDragStart = { startPos ->
                        updateFromOffset(startPos - Offset(centerPx, centerPx))
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        updateFromOffset(thumbOffset + dragAmount)
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onMove(0f, 0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt()) }
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (thumbOffset != Offset.Zero) GamepadButtonPressed else GamepadButton,
                    CircleShape
                )
                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color.White.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

// ─── Joystick flottant (style Mantis Gamepad Pro) ────────────────────────────

/**
 * Zone de capture plein écran (moitié gauche ou droite selon [side]) qui fait
 * apparaître le joystick exactement là où le pouce se pose, puis disparaître
 * dès que le pouce se lève.
 *
 * Comportement :
 *  • IDLE : la zone est invisible. Un cercle fantôme en pointillés indique
 *    la zone d'ancrage possible (optionnel, contrôlé par [showIdleHint]).
 *  • ACTIF : le joystick (base + knob) se matérialise centré sur le premier
 *    point de contact, et le knob suit les mouvements de glissement.
 *  • RELÂCHÉ : le joystick disparaît, [onMove] est rappelé avec (0, 0).
 *
 * @param modifier     Modifier appliqué à la zone de capture (typiquement fillMaxHeight + fillMaxWidth(0.5f))
 * @param baseRadiusDp Rayon visuel de la base du joystick (défaut 65 dp)
 * @param knobRadiusDp Rayon du knob (défaut 26 dp)
 * @param showIdleHint Affiche ou non le cercle fantôme en mode idle
 * @param onMove       Callback normalisé (−1..1, −1..1) appelé à chaque mouvement
 */
@Composable
fun FloatingJoystickZone(
    modifier: Modifier = Modifier,
    baseRadiusDp: Int = 65,
    knobRadiusDp: Int = 26,
    showIdleHint: Boolean = true,
    onMove: (Float, Float) -> Unit
) {
    // Position d'ancrage du joystick (là où le pouce s'est posé)
    var anchorOffset by remember { mutableStateOf<Offset?>(null) }
    // Décalage du knob par rapport au centre de la base
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val maxRadiusPx = with(density) { baseRadiusDp.dp.toPx() }

    fun updateKnob(rawOffset: Offset) {
        val distance = sqrt(rawOffset.x * rawOffset.x + rawOffset.y * rawOffset.y)
        thumbOffset = if (distance > maxRadiusPx) {
            val angle = atan2(rawOffset.y, rawOffset.x)
            Offset(cos(angle) * maxRadiusPx, sin(angle) * maxRadiusPx)
        } else {
            rawOffset
        }
        val normX = (thumbOffset.x / maxRadiusPx).coerceIn(-1f, 1f)
        val normY = -(thumbOffset.y / maxRadiusPx).coerceIn(-1f, 1f)
        onMove(normX, normY)
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                // Fix P0-3 : awaitEachGesture + awaitFirstDown remplace detectDragGestures.
                // detectDragGestures n'appelle onDragStart qu'après franchissement du seuil
                // touchSlop (8–16 dp), retardant l'ancrage. Ici, le joystick s'ancre
                // dès le premier toucher, sans seuil.
                awaitEachGesture {
                    // Attendre le premier contact — SANS seuil de glissement
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    // Ancrer immédiatement au point de contact
                    anchorOffset = down.position
                    thumbOffset = Offset.Zero
                    onMove(0f, 0f)

                    // Suivre les mouvements du même pointerId
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == pointerId }
                            ?: break  // doigt levé

                        if (!pointer.pressed) {
                            // Doigt relevé → reset
                            anchorOffset = null
                            thumbOffset = Offset.Zero
                            onMove(0f, 0f)
                            break
                        }

                        pointer.consume()
                        val anchor = anchorOffset ?: break
                        updateKnob(pointer.position - anchor)
                    }
                }
            }
    ) {
        // ── Cercle fantôme en mode idle ────────────────────────────────────────
        if (showIdleHint && anchorOffset == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size((baseRadiusDp * 2).dp)
                    .border(
                        width = 1.5.dp,
                        color = Color.Cyan.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            )
        }

        // ── Joystick actif ─────────────────────────────────────────────────────
        anchorOffset?.let { anchor ->
            val basePx = baseRadiusDp * 2
            val baseDp = basePx.dp

            // Base du joystick — centrée sur l'ancre
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (anchor.x - maxRadiusPx).roundToInt(),
                            (anchor.y - maxRadiusPx).roundToInt()
                        )
                    }
                    .size(baseDp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .border(2.dp, Color.Cyan.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Knob — se déplace dans la base
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(thumbOffset.x.roundToInt(), thumbOffset.y.roundToInt())
                        }
                        .size((knobRadiusDp * 2).dp)
                        .clip(CircleShape)
                        .background(
                            if (thumbOffset != Offset.Zero) GamepadButtonPressed else GamepadButton,
                            CircleShape
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(Color.White.copy(alpha = 0.55f), CircleShape)
                    )
                }
            }
        }
    }
}
