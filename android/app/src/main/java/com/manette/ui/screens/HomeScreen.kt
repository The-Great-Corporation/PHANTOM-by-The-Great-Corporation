package com.manette.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.manette.hid.BluetoothHidService
import com.manette.hid.HidCapability
import com.manette.ui.components.TGCWatermarkBadge
import com.manette.ui.theme.*
import com.manette.viewmodel.OperationMode

@Composable
fun HomeScreen(
    selectedMode: OperationMode,
    isAutoDiscovered: Boolean = false,
    discoveredIp: String = "",
    backgroundUri: String = "",
    backgroundDim: Float = 0.35f,
    backgroundScale: Float = 1.0f,
    backgroundOffsetX: Float = 0.0f,
    backgroundOffsetY: Float = 0.0f,
    onModeSelect: (OperationMode) -> Unit,
    onStartGame: () -> Unit,
    onOpenConfig: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Vérification des capacités Bluetooth HID
    val hidCapability = remember { BluetoothHidService.checkCapabilities(context) }
    val isPnpSupportedOs = hidCapability.isSupportedOs

    var showUnsupportedDialog by remember { mutableStateOf(false) }
    var showCapabilityMatrixDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF090B10), Color(0xFF101420))
                )
            )
    ) {
        if (backgroundUri.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backgroundUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Fond d'écran global",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = backgroundScale
                        scaleY = backgroundScale
                        translationX = backgroundOffsetX * size.width
                        translationY = backgroundOffsetY * size.height
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundDim.coerceAtLeast(0.45f)))
            )
        }

        val isLandscape = maxWidth > 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = if (isLandscape) 24.dp else 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else 18.dp)
        ) {
            // Top Bar with discreet brand signature in corner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TGCWatermarkBadge(text = "The Great Corporation")
            }

            // Header: "PHANTOM by The Great Corporation"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "PHANTOM",
                    fontSize = if (isLandscape) 42.sp else 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    color = GamepadPrimary
                )
                Text(
                    text = "by The Great Corporation",
                    fontSize = if (isLandscape) 15.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 1.2.sp,
                    color = TGCGold
                )

                // Zero-Friction Auto-Discovery Badge
                if (isAutoDiscovered && selectedMode == OperationMode.THE_GREAT) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF00E676).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "⚡ Serveur PC détecté automatiquement ($discoveredIp)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Mode Selector Cards (Row in Landscape, Column in Portrait)
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    ModeSelectionCard(
                        title = "THE GREAT",
                        badge = "SERVEUR PC",
                        description = "• Émulation Xbox 360 / PS4\n• Détection Wi-Fi automatique\n• Câble USB ADB zéro-latence",
                        isSelected = selectedMode == OperationMode.THE_GREAT,
                        accentColor = GamepadPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = { onModeSelect(OperationMode.THE_GREAT) }
                    )

                    ModeSelectionCard(
                        title = "PLUG & PLAY",
                        badge = if (isPnpSupportedOs) "DIFFUSION PHANTOM" else "ANDROID 9+ REQUIS",
                        description = if (isPnpSupportedOs) {
                            "• Reconnu comme « Phantom »\n• Bluetooth HID Natif sans serveur\n• Compatible PC, Mac, TV & Consoles"
                        } else {
                            "• Profil Bluetooth HID indisponible\n• Nécessite Android 9 Pie ou supérieur\n• Cliquez pour voir les détails"
                        },
                        isSelected = selectedMode == OperationMode.PLUG_AND_PLAY && isPnpSupportedOs,
                        accentColor = if (isPnpSupportedOs) TGCGold else Color.Gray,
                        modifier = Modifier.weight(1f),
                        enabled = isPnpSupportedOs,
                        onClick = {
                            if (isPnpSupportedOs) {
                                onModeSelect(OperationMode.PLUG_AND_PLAY)
                            } else {
                                showUnsupportedDialog = true
                            }
                        }
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ModeSelectionCard(
                        title = "THE GREAT (SERVEUR PC)",
                        badge = "SERVEUR PC",
                        description = "• Émulation Xbox 360 & DualShock 4\n• Détection Wi-Fi automatique sans saisie d'IP\n• Liaison USB ADB zéro-latence",
                        isSelected = selectedMode == OperationMode.THE_GREAT,
                        accentColor = GamepadPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onModeSelect(OperationMode.THE_GREAT) }
                    )

                    ModeSelectionCard(
                        title = "PLUG & PLAY (BLUETOOTH HID)",
                        badge = if (isPnpSupportedOs) "DIFFUSION PHANTOM" else "ANDROID 9+ REQUIS",
                        description = if (isPnpSupportedOs) {
                            "• Nom réseau : « Phantom »\n• Manette standard universelle sans serveur\n• Compatible PC, Smart TV & Consoles"
                        } else {
                            "• Profil Bluetooth HID indisponible\n• Nécessite Android 9 Pie ou supérieur\n• Cliquez pour voir les détails"
                        },
                        isSelected = selectedMode == OperationMode.PLUG_AND_PLAY && isPnpSupportedOs,
                        accentColor = if (isPnpSupportedOs) TGCGold else Color.Gray,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isPnpSupportedOs,
                        onClick = {
                            if (isPnpSupportedOs) {
                                onModeSelect(OperationMode.PLUG_AND_PLAY)
                            } else {
                                showUnsupportedDialog = true
                            }
                        }
                    )
                }
            }

            // Bouton discret Matrice de Capacités
            TextButton(
                onClick = { showCapabilityMatrixDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TGCGold, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Vérifier la compatibilité matérielle de l'appareil",
                    color = TGCGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Action Buttons
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.88f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenConfig,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(GamepadPrimary, TGCGold)))
                    ) {
                        Text("⚙ STUDIO & CONFIG", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = onStartGame,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary)
                    ) {
                        Text("▶ LANCER LA MANETTE", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A0C10))
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartGame,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary)
                    ) {
                        Text("▶ LANCER LA MANETTE", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF0A0C10))
                    }

                    OutlinedButton(
                        onClick = onOpenConfig,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(GamepadPrimary, TGCGold)))
                    ) {
                        Text("⚙ STUDIO DE CONFIGURATION", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Discrete Slogan Legend at the bottom
            Text(
                text = "« The controller you don't hold, the power you command »",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }
    }

    // Dialogue d'information sur appareil non supporté pour Plug & Play
    if (showUnsupportedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsupportedDialog = false },
            containerColor = GamepadSurface,
            title = {
                Text(
                    "Mode Plug & Play Indisponible",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Le profil Bluetooth HID Device natif a été introduit par Google à partir d'Android 9.0 (Pie).",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Text(
                        "Votre appareil : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TGCGold
                    )
                    Text(
                        "Pour jouer sans aucune contrainte de version, utilisez le mode « THE GREAT (Serveur PC) » via Wi-Fi ou câble USB ADB.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onModeSelect(OperationMode.THE_GREAT)
                        showUnsupportedDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary)
                ) {
                    Text("Activer 'THE GREAT'", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsupportedDialog = false }) {
                    Text("Fermer", color = Color.Gray)
                }
            }
        )
    }

    // Matrice de Capacités Matérielles
    if (showCapabilityMatrixDialog) {
        CapabilityMatrixDialog(
            capability = hidCapability,
            onDismiss = { showCapabilityMatrixDialog = false }
        )
    }
}

@Composable
fun ModeSelectionCard(
    title: String,
    badge: String,
    description: String,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(175.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) accentColor else if (!enabled) Color(0xFF202430) else GamepadCard,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GamepadSurface else GamepadBackground.copy(alpha = if (enabled) 0.6f else 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) accentColor else if (enabled) Color.White else Color.Gray
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = if (enabled) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.45f)
            )

            Text(
                text = if (!enabled) "Indisponible sur cette version" else if (isSelected) "✔ MODE ACTIF" else "Cliquer pour activer",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) accentColor else Color.Gray
            )
        }
    }
}

@Composable
fun CapabilityMatrixDialog(
    capability: HidCapability,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GamepadSurface,
        title = {
            Column {
                Text(
                    "Matrice de Capacités Matérielles",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GamepadPrimary
                )
                Text(
                    "Diagnostic Bluetooth HID (Plug & Play)",
                    fontSize = 11.sp,
                    color = TGCGold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CapabilityRow(
                    label = "Version OS (Android 9+)",
                    detail = capability.osVersionName,
                    isSuccess = capability.isSupportedOs
                )
                CapabilityRow(
                    label = "Puce Bluetooth",
                    detail = if (capability.hasBluetoothHardware) "Présente" else "Absente",
                    isSuccess = capability.hasBluetoothHardware
                )
                CapabilityRow(
                    label = "Bluetooth Activé",
                    detail = if (capability.isBluetoothEnabled) "Actif" else "Éteint dans les réglages",
                    isSuccess = capability.isBluetoothEnabled
                )
                CapabilityRow(
                    label = "Permissions Bluetooth",
                    detail = if (capability.hasRequiredPermissions) "Accordées" else "Non accordées",
                    isSuccess = capability.hasRequiredPermissions
                )

                Divider(color = Color(0xFF232838), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (capability.isFullySupported) Color(0xFF00E676).copy(alpha = 0.12f) else TGCGold.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (capability.isFullySupported) {
                            "✔ Cet appareil est 100% prêt pour le mode Plug & Play Bluetooth."
                        } else if (!capability.isSupportedOs) {
                            "⚠ Android 9+ requis pour Plug & Play. Le mode 'THE GREAT' fonctionne parfaitement."
                        } else if (!capability.isBluetoothEnabled) {
                            "⚡ Activez simplement le Bluetooth pour utiliser Plug & Play."
                        } else {
                            "ℹ Certaines permissions ou réglages doivent être complétés."
                        },
                        fontSize = 11.sp,
                        color = if (capability.isFullySupported) Color(0xFF00E676) else TGCGold,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary)
            ) {
                Text("Fermer", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CapabilityRow(
    label: String,
    detail: String,
    isSuccess: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(detail, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Icon(
            imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (isSuccess) Color(0xFF00E676) else Color(0xFFFF5252),
            modifier = Modifier.size(18.dp)
        )
    }
}
