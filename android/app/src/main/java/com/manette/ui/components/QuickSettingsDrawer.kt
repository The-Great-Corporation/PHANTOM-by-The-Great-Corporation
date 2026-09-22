package com.manette.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manette.ui.theme.*

@Composable
fun QuickSettingsDrawer(
    isOpen: Boolean,
    sensitivity: Float,
    deadzone: Float,
    latency: Int,
    connectionType: String,
    onSensitivityChange: (Float) -> Unit,
    onDeadzoneChange: (Float) -> Unit,
    onReconnect: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onClose() }
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(340.dp)
                    .clickable(enabled = false) {}, // Prevent outside click
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                colors = CardDefaults.cardColors(containerColor = GamepadSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top: Header & Close
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("RÉGLAGES RAPIDES", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GamepadPrimary)
                                Text("In-Game Overlay", fontSize = 11.sp, color = TGCGold)
                            }
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Status pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GamepadCard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isConnected = connectionType.lowercase() != "none" && connectionType.isNotBlank()
                                val statusText = when {
                                    latency > 0 -> "${latency} ms"
                                    isConnected -> "Connecté"
                                    else -> "Déconnecté"
                                }
                                val statusColor = when {
                                    !isConnected -> Color(0xFFA0AAB8)
                                    latency in 1..24 -> Color(0xFF00E676)
                                    else -> Color(0xFFFFB800)
                                }
                                Text("Liaison : " + connectionType.uppercase(), fontSize = 12.sp, color = Color.White)
                                Text(
                                    text = statusText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Sensitivity Slider
                        Text("Sensibilité Sticks : " + String.format("%.1f", sensitivity) + "x", fontSize = 13.sp, color = Color.White)
                        Slider(
                            value = sensitivity,
                            onValueChange = onSensitivityChange,
                            valueRange = 0.2f..3.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = GamepadPrimary,
                                activeTrackColor = GamepadPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Deadzone Slider
                        Text("Zone Morte (Deadzone) : " + (deadzone * 100).toInt().toString() + "%", fontSize = 13.sp, color = Color.White)
                        Slider(
                            value = deadzone,
                            onValueChange = onDeadzoneChange,
                            valueRange = 0.0f..0.35f,
                            colors = SliderDefaults.colors(
                                thumbColor = TGCGold,
                                activeTrackColor = TGCGold
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick Reconnect Button
                        OutlinedButton(
                            onClick = onReconnect,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GamepadPrimary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Forcer Reconnexion")
                        }
                    }

                    // Bottom: Full studio button
                    Button(
                        onClick = onOpenFullSettings,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GamepadCard)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ouvrir Studio Complet", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
