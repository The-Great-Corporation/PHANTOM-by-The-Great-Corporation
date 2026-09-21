package com.manette.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.manette.ui.components.ControllerView
import com.manette.ui.components.QuickSettingsDrawer
import com.manette.ui.components.TGCWatermarkBadge
import com.manette.ui.theme.*
import com.manette.viewmodel.GameViewModel
import com.manette.viewmodel.OperationMode

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onOpenFullConfig: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val backgroundUri by viewModel.backgroundUri.collectAsState()
    val backgroundDim by viewModel.backgroundDim.collectAsState()
    val backgroundScale by viewModel.backgroundScale.collectAsState()
    val backgroundOffsetX by viewModel.backgroundOffsetX.collectAsState()
    val backgroundOffsetY by viewModel.backgroundOffsetY.collectAsState()
    val skin by viewModel.skin.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()
    val deadzone by viewModel.deadzone.collectAsState()
    val quickSettingsOpen by viewModel.quickSettingsOpen.collectAsState()
    val operationMode by viewModel.operationMode.collectAsState()
    val isAutoDiscovered by viewModel.isAutoDiscovered.collectAsState()
    val hidConnected by viewModel.hidConnected.collectAsState()
    val hidDeviceName by viewModel.hidDeviceName.collectAsState()
    val customLayout by viewModel.customLayout.collectAsState()

    // Verrouillage en mode paysage pour la manette
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val originalOrientation = activity?.requestedOrientation
            ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
            // Arrêter le service HID si on quitte l'écran en mode PLUG & PLAY
            if (operationMode == OperationMode.PLUG_AND_PLAY) {
                viewModel.stopHidService()
            }
        }
    }

    // Libération de l'état neutre quand l'app passe en arrière-plan (A5 : aucune touche collée)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.releaseAllInputs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Connexion automatique selon le mode actif
    LaunchedEffect(Unit) {
        viewModel.connectForCurrentMode()
    }

    val statusText = when (operationMode) {
        OperationMode.THE_GREAT -> {
            if (connectionState.connected) {
                "${connectionState.connectionType.uppercase()} • ${connectionState.latency} ms"
            } else if (isAutoDiscovered) {
                "Reconnexion…"
            } else {
                "Serveur introuvable"
            }
        }
        OperationMode.PLUG_AND_PLAY -> {
            if (hidConnected) {
                "HID • Connecté à ${hidDeviceName ?: "Hôte"}"
            } else {
                "Diffusion « Phantom » • Prêt à appairer"
            }
        }
    }

    val statusColor = when (operationMode) {
        OperationMode.THE_GREAT -> if (connectionState.connected) Color(0xFF00E676) else Color(0xFFFF5252)
        OperationMode.PLUG_AND_PLAY -> if (hidConnected) Color(0xFF00E676) else TGCGold
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GamepadBackground)
    ) {
        // ── 1. FOND PERSONNALISÉ ───────────────────────────────────────────────
        if (backgroundUri.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backgroundUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Fond personnalisé",
                contentScale = ContentScale.Fit,
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
                    .background(Color.Black.copy(alpha = backgroundDim))
            )
        }

        // ── 2. MANETTE PLEIN ÉCRAN ────────────────────────────────────────────
        ControllerView(
            modifier = Modifier.fillMaxSize(),
            skin = skin,
            positions = customLayout,
            onButtonPress = { button, pressed ->
                viewModel.sendButtonPress(button, pressed)
            },
            onJoystickMove = { stick, x, y ->
                viewModel.sendJoystickMove(stick, x, y)
            }
        )

        // ── 3. BARRE DE STATUT DISCRÈTE EN HAUT ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bouton retour discret
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Accueil",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Pill de statut connexion
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Bouton Quick Settings discret
            IconButton(
                onClick = { viewModel.toggleQuickSettings() },
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "Réglages rapides",
                    tint = TGCGold,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── 4. WATERMARK DISCRET EN BAS ───────────────────────────────────────
        TGCWatermarkBadge(
            modifier = Modifier.align(Alignment.BottomCenter),
            text = "PHANTOM by The Great Corporation",
            color = Color.White.copy(alpha = 0.18f)
        )

        // ── 5. TIROIR QUICK SETTINGS ──────────────────────────────────────────
        QuickSettingsDrawer(
            isOpen = quickSettingsOpen,
            sensitivity = sensitivity,
            deadzone = deadzone,
            latency = connectionState.latency,
            connectionType = when (operationMode) {
                OperationMode.THE_GREAT -> connectionState.connectionType
                OperationMode.PLUG_AND_PLAY -> "bluetooth_hid"
            },
            onSensitivityChange = { viewModel.setSensitivity(it) },
            onDeadzoneChange = { viewModel.setDeadzone(it) },
            onReconnect = { viewModel.connectForCurrentMode() },
            onOpenFullSettings = {
                viewModel.closeQuickSettings()
                onOpenFullConfig()
            },
            onClose = { viewModel.closeQuickSettings() }
        )
    }
}
