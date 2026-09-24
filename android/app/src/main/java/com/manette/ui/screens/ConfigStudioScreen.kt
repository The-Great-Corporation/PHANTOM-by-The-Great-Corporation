package com.manette.ui.screens

import android.net.Uri
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.manette.config.SkinLoader
import com.manette.ui.components.ReactiveGamepadButton
import com.manette.ui.components.HapticCategory
import com.manette.ui.components.SkinButtonTheme
import com.manette.ui.components.TGCWatermarkBadge
import com.manette.ui.components.getSkinTheme
import com.manette.ui.theme.*
import com.manette.viewmodel.GameViewModel
import com.google.zxing.integration.android.IntentIntegrator

@Composable
fun ConfigStudioScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit,
    onOpenLayoutEditor: () -> Unit,
    onStartGame: () -> Unit
) {
    val serverIp by viewModel.serverIp.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val connectionType by viewModel.connectionType.collectAsState()
    val backgroundUri by viewModel.backgroundUri.collectAsState()
    val backgroundDim by viewModel.backgroundDim.collectAsState()
    val backgroundScale by viewModel.backgroundScale.collectAsState()
    val backgroundOffsetX by viewModel.backgroundOffsetX.collectAsState()
    val backgroundOffsetY by viewModel.backgroundOffsetY.collectAsState()
    val skin by viewModel.skin.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()
    val deadzone by viewModel.deadzone.collectAsState()
    val floatingSticks by viewModel.floatingSticks.collectAsState()
    val isAutoDiscovered by viewModel.isAutoDiscovered.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val pairingRequired by viewModel.pairingRequired.collectAsState()
    val pairingMessage by viewModel.pairingMessage.collectAsState()

    var advancedExpanded by remember { mutableStateOf(false) }
    var pendingBgUri by remember { mutableStateOf<String?>(null) }
    var tempDim by remember { mutableStateOf(0.35f) }
    var tempScale by remember { mutableStateOf(1.0f) }
    var tempOffsetX by remember { mutableStateOf(0.0f) }
    var tempOffsetY by remember { mutableStateOf(0.0f) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    // Skin : preview avant adoption
    var showSkinPreview by remember { mutableStateOf(false) }
    var pendingSkin by remember { mutableStateOf(skin) }

    val context = LocalContext.current
    val qrScanner = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        val payload = scanResult?.contents
        if (payload.isNullOrBlank()) {
            Toast.makeText(context, "QR Code vide ou invalide.", Toast.LENGTH_SHORT).show()
        } else {
            val error = viewModel.savePairingPayload(payload)
            if (error != null) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Sélecteur d'image avec permission persistante et aperçu/ajustement interactif préalable
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* URI ne supporte pas la permission persistante */ }
            pendingBgUri = it.toString()
            tempDim = backgroundDim
            tempScale = backgroundScale
            tempOffsetX = backgroundOffsetX
            tempOffsetY = backgroundOffsetY
            showAdjustDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GamepadBackground)
    ) {
        if (backgroundUri.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backgroundUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
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
                    .background(Color.Black.copy(alpha = backgroundDim.coerceAtLeast(0.70f)))
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = GamepadPrimary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "STUDIO DE CONFIGURATION",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GamepadPrimary
                        )
                        Text(
                            "PHANTOM by The Great Corporation",
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            color = TGCGold
                        )
                    }

                    TGCWatermarkBadge()
                }
            }
        },
        bottomBar = {
            Surface(
                color = GamepadBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        viewModel.connect()
                        onStartGame()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary)
                ) {
                    Text(
                        "SAUVEGARDER & LANCER LA MANETTE ▶",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Section 1 : Connexion (Zéro Friction) ─────────────────────────
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = GamepadSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "📡 CONNEXION AU SERVEUR PC",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GamepadPrimary
                    )

                    if (isAutoDiscovered) {
                        // ✅ Serveur détecté — badge vert
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚡", fontSize = 20.sp)
                                Column {
                                    Text(
                                        if (connectionState.connected) "Connexion UDP établie" else "Serveur détecté automatiquement",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E676)
                                    )
                                    Text(
                                        if (connectionState.connected) "IP : ${serverIp} — handshake confirmé"
                                        else "IP : ${serverIp} — appairage en cours ou requis",
                                        fontSize = 11.sp,
                                        color = Color(0xFF00E676).copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    } else {
                        // 🔍 Recherche en cours — badge jaune + bouton relancer
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = TGCGold.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "🔍 Recherche du serveur PC en cours…",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TGCGold
                                )
                                Text(
                                    "Assurez-vous que PHANTOM Server est lancé sur votre PC et que le téléphone est sur le même réseau Wi-Fi.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    lineHeight = 16.sp
                                )
                                Button(
                                    onClick = { viewModel.scanForTgcServer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    Text(
                                        "↺ Relancer la détection",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }

                    // ── Protocole de transmission exposé en 1er plan ─────────
                    Text("Protocole de transmission :", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "udp" to "Wi-Fi UDP",
                            "usb" to "Câble USB",
                            "websocket" to "WebSocket"
                        ).forEach { (type, label) ->
                            val isSel = connectionType == type
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) TGCGold else GamepadCard,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setConnectionType(type) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.Black else Color.White,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // ── Statut d'appairage & Scan QR direct (Discret) ─────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (pairingRequired) Color(0xFFFFB74D) else Color(0xFF00E676),
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Text(
                                if (pairingRequired) "Appairage serveur requis" else "Serveur appairé (Keystore)",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GamepadPrimary,
                            modifier = Modifier.clickable {
                                val activity = context as? Activity
                                if (activity == null) {
                                    Toast.makeText(context, "Scanner indisponible.", Toast.LENGTH_SHORT).show()
                                } else {
                                    val intent = IntentIntegrator(activity)
                                        .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                                        .setPrompt("Scannez le QR PHANTOM affiché par le serveur")
                                        .setBeepEnabled(false)
                                        .createScanIntent()
                                    qrScanner.launch(intent)
                                }
                            }
                        ) {
                            Text(
                                "📷 Scanner QR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    pairingMessage?.let {
                        Text(
                            it,
                            fontSize = 11.sp,
                            color = if (pairingRequired) Color(0xFFFFB74D) else Color.White
                        )
                    }

                    // ── Accordéon Réglages Avancés (IP, Port & Saisie manuelle d'appairage) ────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advancedExpanded = !advancedExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚙ Réglages avancés (IP, Port & Clés d'appairage)",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (advancedExpanded) "▲" else "▼",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    if (advancedExpanded) {
                        var pairingText by remember { mutableStateOf("") }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = serverIp,
                                onValueChange = { viewModel.setServerIp(it) },
                                label = { Text("IP manuelle du PC") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GamepadPrimary,
                                    unfocusedBorderColor = Color.Gray
                                )
                            )
                            OutlinedTextField(
                                value = serverPort,
                                onValueChange = { viewModel.setServerPort(it) },
                                label = { Text("Port (Défaut : 8888)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GamepadPrimary,
                                    unfocusedBorderColor = Color.Gray
                                )
                            )
                            Button(
                                onClick = { viewModel.connect() },
                                colors = ButtonDefaults.buttonColors(containerColor = GamepadCard),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Text(
                                    "Connexion manuelle",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GamepadPrimary
                                )
                            }

                            Divider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                "🔐 Saisie manuelle du Payload d'appairage JSON",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GamepadPrimary
                            )
                            OutlinedTextField(
                                value = pairingText,
                                onValueChange = { pairingText = it },
                                label = { Text("Payload JSON d'appairage") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GamepadPrimary,
                                    unfocusedBorderColor = Color.Gray
                                )
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val error = viewModel.savePairingPayload(pairingText)
                                        if (error == null) pairingText = ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary)
                                ) { Text("Enregistrer les clés", color = Color.Black, fontSize = 11.sp) }
                                OutlinedButton(
                                    onClick = { viewModel.clearPairing() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Révoquer clés", color = Color.White, fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }

            // ── Section 2 : Disposition Personnalisée des Touches ─────────────
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = GamepadSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "🎮 DISPOSITION DES TOUCHES",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GamepadPrimary
                    )

                    Text(
                        "Ajustez et déplacez librement chaque commande (sticks analogiques, croix directionnelle, touches ABXY, gâchettes LT/RT, bumpers LB/RB, touches menu) avec notre éditeur visuel plein écran.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = onOpenLayoutEditor,
                        colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            "✏ PERSONNALISER LA DISPOSITION DES TOUCHES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }
            }

            // ── Section 2 : Personnalisation Visuelle ─────────────────────────
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = GamepadSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "🎨 PERSONNALISATION VISUELLE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GamepadPrimary
                    )

                    Text(
                        "Arrière-plan (Image ou GIF animé) :",
                        fontSize = 12.sp,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { imagePicker.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = GamepadCard),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = GamepadPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choisir Image / GIF…", color = Color.White, fontSize = 12.sp)
                        }

                        if (backgroundUri.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    pendingBgUri = backgroundUri
                                    tempDim = backgroundDim
                                    tempScale = backgroundScale
                                    tempOffsetX = backgroundOffsetX
                                    tempOffsetY = backgroundOffsetY
                                    showAdjustDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = TGCGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ajuster", color = TGCGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            IconButton(onClick = { viewModel.setBackgroundUri("") }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Supprimer fond",
                                    tint = Color(0xFFFF5252)
                                )
                            }
                            Text(
                                "Actif ✔",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                "Fond par défaut (Noir gaming)",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (backgroundUri.isNotEmpty()) {
                        Text(
                            "Assombrissement : ${(backgroundDim * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Slider(
                            value = backgroundDim,
                            onValueChange = { viewModel.setBackgroundDim(it) },
                            valueRange = 0.0f..0.85f,
                            colors = SliderDefaults.colors(
                                thumbColor = GamepadPrimary,
                                activeTrackColor = GamepadPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Skin des Touches :", fontSize = 12.sp, color = Color.White)
                    Text(
                        "Appuyez sur un skin pour prévisualiser — le skin actuel est marqué ✔",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "xbox"        to "Xbox",
                            "playstation" to "PS",
                            "nintendo"    to "Nintendo",
                            "cyberpunk"   to "Néon",
                            "ghost"       to "Ghost"
                        ).forEach { (key, label) ->
                            val isActive = skin == key
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) GamepadPrimary else GamepadCard,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        pendingSkin = key
                                        showSkinPreview = true
                                    }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    if (isActive) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) Color.Black else Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Section 3 : Calibration ───────────────────────────────────────
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = GamepadSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "🎯 CALIBRATION DES CONTRÔLES",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GamepadPrimary
                    )

                    Text(
                        "Sensibilité Sticks : ${String.format("%.1f", sensitivity)}x",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Slider(
                        value = sensitivity,
                        onValueChange = { viewModel.setSensitivity(it) },
                        valueRange = 0.2f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = GamepadPrimary,
                            activeTrackColor = GamepadPrimary
                        )
                    )

                    Text(
                        "Zone Morte (Deadzone) : ${(deadzone * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Slider(
                        value = deadzone,
                        onValueChange = { viewModel.setDeadzone(it) },
                        valueRange = 0.0f..0.35f,
                        colors = SliderDefaults.colors(
                            thumbColor = TGCGold,
                            activeTrackColor = TGCGold
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Stick Gauche : Mode Tactile", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(
                                if (floatingSticks) "Flottant (s'ancre sous le doigt et accompagne le pouce)" else "Fixe (position verrouillée sur le layout)",
                                fontSize = 11.sp,
                                color = if (floatingSticks) GamepadPrimary else TGCGold
                            )
                        }
                        Switch(
                            checked = floatingSticks,
                            onCheckedChange = { viewModel.setFloatingSticks(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GamepadPrimary,
                                checkedTrackColor = GamepadPrimary.copy(alpha = 0.5f),
                                uncheckedThumbColor = TGCGold,
                                uncheckedTrackColor = TGCGold.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ── Dialogue de preview de skin — adoption uniquement si l'utilisateur valide ──
    if (showSkinPreview) {
        SkinPreviewDialog(
            skinId = pendingSkin,
            currentSkinId = skin,
            onAdopt = {
                viewModel.setSkin(pendingSkin)
                showSkinPreview = false
                Toast.makeText(context, "Skin \"${pendingSkin}\" adopté !", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSkinPreview = false }
        )
    }

    // ── Dialogue d'ajustement interactif préalable de l'arrière-plan ──────
    if (showAdjustDialog && pendingBgUri != null) {
        BackgroundAdjustDialog(
            uri = pendingBgUri!!,
            dim = tempDim,
            scale = tempScale,
            offsetX = tempOffsetX,
            offsetY = tempOffsetY,
            onDimChange = { tempDim = it },
            onScaleChange = { tempScale = it },
            onOffsetXChange = { tempOffsetX = it },
            onOffsetYChange = { tempOffsetY = it },
            onReset = {
                tempScale = 1.0f
                tempOffsetX = 0.0f
                tempOffsetY = 0.0f
            },
            onConfirm = {
                viewModel.setBackground(pendingBgUri!!, tempDim, tempScale, tempOffsetX, tempOffsetY)
                showAdjustDialog = false
                Toast.makeText(context, "Arrière-plan appliqué avec succès !", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showAdjustDialog = false
            }
        )
    }
}
}

/**
 * Dialogue de prévisualisation d'un skin avant adoption.
 * Affiche les 4 boutons ABXY + une mini croix directionnelle dans les couleurs du skin sélectionné.
 * L'utilisateur peut annuler (aucune modification) ou adopter (viewModel.setSkin est appelé depuis le parent).
 */
@Composable
fun SkinPreviewDialog(
    skinId: String,
    currentSkinId: String,
    onAdopt: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = getSkinTheme(context, skinId)
    val skinMeta = remember(skinId) {
        SkinLoader.listSkins(context).find { it.skinId == skinId }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GamepadSurface,
        title = {
            Column {
                Text(
                    skinMeta?.displayName ?: skinId.replaceFirstChar { it.uppercaseChar() },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = GamepadPrimary
                )
                Text(
                    skinMeta?.description ?: "Prévisualisation du skin",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    lineHeight = 14.sp
                )
                if (currentSkinId == skinId) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF00E676).copy(alpha = 0.15f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            "✔ Skin actuellement actif",
                            fontSize = 10.sp,
                            color = Color(0xFF00E676),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Mini manette preview ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.2f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, GamepadPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ── D-Pad mockup ──────────────────────────────────────
                        Box(
                            modifier = Modifier.size(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val dpadSize = 28.dp
                            val dpadShape = RoundedCornerShape(4.dp)
                            // Haut
                            Box(
                                modifier = Modifier
                                    .offset(y = (-30).dp)
                                    .size(dpadSize)
                                    .background(theme.dpadColor, dpadShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), dpadShape)
                            )
                            // Bas
                            Box(
                                modifier = Modifier
                                    .offset(y = 30.dp)
                                    .size(dpadSize)
                                    .background(theme.dpadColor, dpadShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), dpadShape)
                            )
                            // Gauche
                            Box(
                                modifier = Modifier
                                    .offset(x = (-30).dp)
                                    .size(dpadSize)
                                    .background(theme.dpadColor, dpadShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), dpadShape)
                            )
                            // Droite
                            Box(
                                modifier = Modifier
                                    .offset(x = 30.dp)
                                    .size(dpadSize)
                                    .background(theme.dpadColor, dpadShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), dpadShape)
                            )
                            // Centre
                            Box(
                                modifier = Modifier
                                    .size(dpadSize)
                                    .background(theme.dpadColor, dpadShape)
                            )
                        }

                        // ── Cluster ABXY ──────────────────────────────────────
                        Box(
                            modifier = Modifier.size(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val btnSize = 30.dp
                            // Y — haut
                            Box(
                                modifier = Modifier
                                    .offset(y = (-30).dp)
                                    .size(btnSize)
                                    .clip(CircleShape)
                                    .background(theme.yColor, CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(theme.yLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f)) }
                            // A — bas
                            Box(
                                modifier = Modifier
                                    .offset(y = 30.dp)
                                    .size(btnSize)
                                    .clip(CircleShape)
                                    .background(theme.aColor, CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(theme.aLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f)) }
                            // X — gauche
                            Box(
                                modifier = Modifier
                                    .offset(x = (-30).dp)
                                    .size(btnSize)
                                    .clip(CircleShape)
                                    .background(theme.xColor, CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(theme.xLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f)) }
                            // B — droite
                            Box(
                                modifier = Modifier
                                    .offset(x = 30.dp)
                                    .size(btnSize)
                                    .clip(CircleShape)
                                    .background(theme.bColor, CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(theme.bLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f)) }
                        }
                    }

                    // Label centré en bas de la preview
                    Text(
                        text = "Prévisualisation — non interactive",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp),
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.35f)
                    )
                }

                // ── Bumpers mockup ligne compacte ─────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("LT" to theme.bumperColor, "LB" to theme.bumperColor,
                           "RB" to theme.bumperColor, "RT" to theme.bumperColor)
                        .forEach { (label, color) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = color,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.75f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAdopt,
                colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary),
                shape = RoundedCornerShape(8.dp),
                enabled = currentSkinId != skinId
            ) {
                Text(
                    if (currentSkinId == skinId) "Skin actif" else "Adopter ce skin",
                    color = if (currentSkinId == skinId) Color.Black.copy(alpha = 0.5f) else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Color.Gray, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun BackgroundAdjustDialog(
    uri: String,
    dim: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onDimChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onOffsetXChange: (Float) -> Unit,
    onOffsetYChange: (Float) -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GamepadSurface,
        title = {
            Column {
                Text(
                    "Ajustement de l'Arrière-Plan",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = GamepadPrimary
                )
                Text(
                    "PHANTOM by The Great Corporation",
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = TGCGold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "💡 Pincez ou glissez sur l'image pour cadrer, ou utilisez les réglettes :",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                // Cadre de prévisualisation avec gestes tactiles — ratio proche d'un écran de téléphone
                // pour que ce qu'on cadre ici corresponde à ce qui s'affichera réellement en jeu
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .aspectRatio(9f / 19.5f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.5.dp, GamepadPrimary, RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // 0.3f minimum : permet de dézoomer sous le niveau de rognage
                                // automatique pour revoir l'image entière avant de choisir le cadrage
                                onScaleChange((scale * zoom).coerceIn(0.3f, 4.0f))
                                onOffsetXChange((offsetX + pan.x / 400f).coerceIn(-0.6f, 0.6f))
                                onOffsetYChange((offsetY + pan.y / 250f).coerceIn(-0.6f, 0.6f))
                            }
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Aperçu arrière-plan",
                        // Fit plutôt que Crop : l'image entière est visible par défaut,
                        // c'est le zoom (scale) qui décide ensuite du rognage, pas le rendu initial
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX * size.width
                                translationY = offsetY * size.height
                            }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = dim))
                    )
                    Text(
                        text = "Aperçu Manette (Pincer pour zoomer)",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Slider Zoom / Échelle
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Zoom (Échelle) :", fontSize = 11.sp, color = Color.White)
                        Text(String.format("%.2fx", scale), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GamepadPrimary)
                    }
                    Slider(
                        value = scale,
                        onValueChange = onScaleChange,
                        valueRange = 0.3f..4.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = GamepadPrimary,
                            activeTrackColor = GamepadPrimary
                        )
                    )
                }

                // Slider Cadrage Horizontal (X)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cadrage horizontal (X) :", fontSize = 11.sp, color = Color.White)
                        Text("${(offsetX * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TGCGold)
                    }
                    Slider(
                        value = offsetX,
                        onValueChange = onOffsetXChange,
                        valueRange = -0.6f..0.6f,
                        colors = SliderDefaults.colors(
                            thumbColor = TGCGold,
                            activeTrackColor = TGCGold
                        )
                    )
                }

                // Slider Cadrage Vertical (Y)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cadrage vertical (Y) :", fontSize = 11.sp, color = Color.White)
                        Text("${(offsetY * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TGCGold)
                    }
                    Slider(
                        value = offsetY,
                        onValueChange = onOffsetYChange,
                        valueRange = -0.6f..0.6f,
                        colors = SliderDefaults.colors(
                            thumbColor = TGCGold,
                            activeTrackColor = TGCGold
                        )
                    )
                }

                // Slider Assombrissement
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Assombrissement :", fontSize = 11.sp, color = Color.White)
                        Text("${(dim * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GamepadPrimary)
                    }
                    Slider(
                        value = dim,
                        onValueChange = onDimChange,
                        valueRange = 0.0f..0.85f,
                        colors = SliderDefaults.colors(
                            thumbColor = GamepadPrimary,
                            activeTrackColor = GamepadPrimary
                        )
                    )
                }

                // Bouton réinitialiser cadrage
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = TGCGold, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Réinitialiser le cadrage (1.0x)", color = TGCGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Valider & Appliquer", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = Color.Gray, fontSize = 12.sp)
            }
        }
    )
}
