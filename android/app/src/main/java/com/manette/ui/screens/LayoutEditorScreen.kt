package com.manette.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.manette.config.ButtonPosition
import com.manette.config.LayoutDefaults
import com.manette.ui.components.ControllerView
import com.manette.ui.components.getSkinTheme
import com.manette.ui.theme.*
import com.manette.viewmodel.GameViewModel
import kotlinx.coroutines.launch

@Composable
fun LayoutEditorScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Verrouillage en mode paysage pour l'éditeur
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val originalOrientation = activity?.requestedOrientation
            ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val backgroundUri by viewModel.backgroundUri.collectAsState()
    val backgroundDim by viewModel.backgroundDim.collectAsState()
    val backgroundScale by viewModel.backgroundScale.collectAsState()
    val backgroundOffsetX by viewModel.backgroundOffsetX.collectAsState()
    val backgroundOffsetY by viewModel.backgroundOffsetY.collectAsState()
    val skin by viewModel.skin.collectAsState()
    val savedPositions by viewModel.customLayout.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val activeProfileFilename by viewModel.activeProfileFilename.collectAsState()
    val profilesList by viewModel.profilesList.collectAsState()

    val theme = getSkinTheme(context, skin)

    // Mode d'édition : "controls" (touches), "background" (arrière-plan), ou "test" (zone de test)
    var editorMode by remember { mutableStateOf("controls") }

    // État local de l'arrière-plan
    var bgScale by remember(backgroundScale) { mutableStateOf(backgroundScale) }
    var bgOffsetX by remember(backgroundOffsetX) { mutableStateOf(backgroundOffsetX) }
    var bgOffsetY by remember(backgroundOffsetY) { mutableStateOf(backgroundOffsetY) }
    var bgDim by remember(backgroundDim) { mutableStateOf(backgroundDim) }

    // Copie de travail locale
    var positions by remember(savedPositions) {
        mutableStateOf(LayoutDefaults.getEffectivePositions(savedPositions).toMutableMap())
    }

    // ── Undo / Redo Stacks ──────────────────────────────────────────────────
    val undoStack = remember { mutableStateListOf<Map<String, ButtonPosition>>() }
    val redoStack = remember { mutableStateListOf<Map<String, ButtonPosition>>() }

    fun pushUndoSnapshot() {
        undoStack.add(positions.toMap())
        redoStack.clear()
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(positions.toMap())
            positions = previous.toMutableMap()
            Toast.makeText(context, "Annulation (Undo)", Toast.LENGTH_SHORT).show()
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(positions.toMap())
            positions = next.toMutableMap()
            Toast.makeText(context, "Rétablissement (Redo)", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedKey by remember { mutableStateOf<String?>("left_stick") }
    var testInputFeedback by remember { mutableStateOf("Touchez les commandes pour vérifier le ressenti") }

    // Dialogues multi-profils
    var showProfilesDialog by remember { mutableStateOf(false) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Sélecteur d'arrière-plan direct dans l'éditeur
    val bgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.setBackgroundUri(it.toString())
        }
    }

    // Export .phantom launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        val success = viewModel.exportActiveProfileToStream(stream)
                        if (success) {
                            Toast.makeText(context, "Profil exporté avec succès (.phantom) !", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Erreur lors de l'export du profil", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Erreur export: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Import .phantom launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val filename = "import_${System.currentTimeMillis() % 10000}.phantom"
                        val success = viewModel.importProfileFromStream(stream, filename)
                        if (success) {
                            positions = LayoutDefaults.getEffectivePositions(viewModel.customLayout.value).toMutableMap()
                            Toast.makeText(context, "Profil .phantom importé avec succès !", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Format de profil invalide ou corrompu", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Erreur import: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GamepadBackground)
    ) {
        // ── 1. Fond d'écran global avec dim & cadrage en temps réel ──────────
        if (backgroundUri.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backgroundUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = bgScale
                        scaleY = bgScale
                        translationX = bgOffsetX * size.width
                        translationY = bgOffsetY * size.height
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = bgDim))
            )
        }

        // Grille d'aide discrète
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (editorMode == "background") 0.10f else 0.35f))
        )

        // ── Zone tactile plein écran pour manipuler l'arrière-plan ────────────
        if (editorMode == "background") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            bgScale = (bgScale * zoom).coerceIn(1.0f, 5.0f)
                            bgOffsetX = (bgOffsetX + pan.x / (1600f * bgScale)).coerceIn(-1.0f, 1.0f)
                            bgOffsetY = (bgOffsetY + pan.y / (720f * bgScale)).coerceIn(-1.0f, 1.0f)
                        }
                    }
            )
        }

        // ── 2. Zone Canvas Éditeur ou Zone de Test ─────────────────────────────
        if (editorMode == "test") {
            // Zone de Test intégrée
            Box(modifier = Modifier.fillMaxSize()) {
                ControllerView(
                    positions = positions,
                    skin = skin,
                    floatingSticks = true,
                    onButtonPress = { btn, pressed ->
                        testInputFeedback = "Touche $btn -> ${if (pressed) "PRESSÉE" else "RELÂCHÉE"}"
                    },
                    onJoystickMove = { stick, x, y ->
                        testInputFeedback = "Stick $stick -> X=${String.format("%.2f", x)} Y=${String.format("%.2f", y)}"
                    }
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.88f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🧪 ZONE DE TEST ACTIVE :", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(testInputFeedback, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        } else {
            // Zone d'édition (Controls ou Background)
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val screenW = maxWidth.value
                val screenH = maxHeight.value
                val isControlsMode = editorMode == "controls"

                positions.forEach { (key, pos) ->
                    val isSelected = selectedKey == key && isControlsMode

                    when (key) {
                        "left_stick", "right_stick" -> {
                            val size = (140f * pos.size).dp
                            EditorDraggableControl(
                                x = pos.x,
                                y = pos.y,
                                itemWidth = size,
                                itemHeight = size,
                                screenW = screenW,
                                screenH = screenH,
                                enabled = isControlsMode,
                                onSelect = { selectedKey = key },
                                onDragStart = { pushUndoSnapshot() },
                                onDrag = { dx, dy ->
                                    val cur = positions[key] ?: return@EditorDraggableControl
                                    val newX = (cur.x + dx / screenW).coerceIn(0.01f, 0.99f)
                                    val newY = (cur.y + dy / screenH).coerceIn(0.02f, 0.98f)
                                    positions = positions.toMutableMap().apply {
                                        this[key] = cur.copy(x = newX, y = newY)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (isControlsMode) 1.0f else 0.50f }
                                        .clip(CircleShape)
                                        .background(Color(0xFF161B26).copy(alpha = 0.85f))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.5.dp,
                                            color = if (isSelected) GamepadPrimary else Color(0xFF353B4E),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size((50f * pos.size).dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) GamepadPrimary else TGCGold.copy(alpha = 0.8f))
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (key == "left_stick") "L-STICK" else "R-STICK",
                                            fontSize = (10 * pos.size).toInt().coerceAtLeast(8).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        "dpad" -> {
                            val size = (130f * pos.size).dp
                            val btnSize = (42f * pos.size).dp
                            EditorDraggableControl(
                                x = pos.x,
                                y = pos.y,
                                itemWidth = size,
                                itemHeight = size,
                                screenW = screenW,
                                screenH = screenH,
                                enabled = isControlsMode,
                                onSelect = { selectedKey = key },
                                onDragStart = { pushUndoSnapshot() },
                                onDrag = { dx, dy ->
                                    val cur = positions[key] ?: return@EditorDraggableControl
                                    val newX = (cur.x + dx / screenW).coerceIn(0.01f, 0.99f)
                                    val newY = (cur.y + dy / screenH).coerceIn(0.02f, 0.98f)
                                    positions = positions.toMutableMap().apply {
                                        this[key] = cur.copy(x = newX, y = newY)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (isControlsMode) 1.0f else 0.50f }
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) GamepadPrimary else Color.Transparent,
                                            shape = RoundedCornerShape(16.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopCenter).size(btnSize),
                                        shape = RoundedCornerShape(8.dp),
                                        color = theme.dpadColor
                                    ) { Box(contentAlignment = Alignment.Center) { Text("▲", color = Color.White) } }
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomCenter).size(btnSize),
                                        shape = RoundedCornerShape(8.dp),
                                        color = theme.dpadColor
                                    ) { Box(contentAlignment = Alignment.Center) { Text("▼", color = Color.White) } }
                                    Surface(
                                        modifier = Modifier.align(Alignment.CenterStart).size(btnSize),
                                        shape = RoundedCornerShape(8.dp),
                                        color = theme.dpadColor
                                    ) { Box(contentAlignment = Alignment.Center) { Text("◀", color = Color.White) } }
                                    Surface(
                                        modifier = Modifier.align(Alignment.CenterEnd).size(btnSize),
                                        shape = RoundedCornerShape(8.dp),
                                        color = theme.dpadColor
                                    ) { Box(contentAlignment = Alignment.Center) { Text("▶", color = Color.White) } }
                                }
                            }
                        }

                        "abxy" -> {
                            val clusterSize = (130f * pos.size).dp
                            val btnSize = (44f * pos.size).dp

                            EditorDraggableControl(
                                x = pos.x,
                                y = pos.y,
                                itemWidth = clusterSize,
                                itemHeight = clusterSize,
                                screenW = screenW,
                                screenH = screenH,
                                enabled = isControlsMode,
                                onSelect = { selectedKey = key },
                                onDragStart = { pushUndoSnapshot() },
                                onDrag = { dx, dy ->
                                    val cur = positions[key] ?: return@EditorDraggableControl
                                    val newX = (cur.x + dx / screenW).coerceIn(0.01f, 0.99f)
                                    val newY = (cur.y + dy / screenH).coerceIn(0.02f, 0.98f)
                                    positions = positions.toMutableMap().apply {
                                        this[key] = cur.copy(x = newX, y = newY)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (isControlsMode) 1.0f else 0.50f }
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isSelected) GamepadPrimary.copy(alpha = 0.15f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) GamepadPrimary else TGCGold.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(20.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopCenter).size(btnSize),
                                        shape = CircleShape,
                                        color = theme.yColor,
                                        shadowElevation = 3.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = theme.yLabel,
                                                fontSize = (16 * pos.size).toInt().coerceAtLeast(10).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomCenter).size(btnSize),
                                        shape = CircleShape,
                                        color = theme.aColor,
                                        shadowElevation = 3.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = theme.aLabel,
                                                fontSize = (16 * pos.size).toInt().coerceAtLeast(10).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.align(Alignment.CenterStart).size(btnSize),
                                        shape = CircleShape,
                                        color = theme.xColor,
                                        shadowElevation = 3.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = theme.xLabel,
                                                fontSize = (16 * pos.size).toInt().coerceAtLeast(10).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.align(Alignment.CenterEnd).size(btnSize),
                                        shape = CircleShape,
                                        color = theme.bColor,
                                        shadowElevation = 3.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = theme.bLabel,
                                                fontSize = (16 * pos.size).toInt().coerceAtLeast(10).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "btn_lt", "btn_lb", "btn_rt", "btn_rb" -> {
                            val size = (54f * pos.size).dp
                            val lbl = when (key) {
                                "btn_lt" -> "LT"
                                "btn_lb" -> "LB"
                                "btn_rt" -> "RT"
                                else -> "RB"
                            }

                            EditorDraggableControl(
                                x = pos.x,
                                y = pos.y,
                                itemWidth = size,
                                itemHeight = size,
                                screenW = screenW,
                                screenH = screenH,
                                enabled = isControlsMode,
                                onSelect = { selectedKey = key },
                                onDragStart = { pushUndoSnapshot() },
                                onDrag = { dx, dy ->
                                    val cur = positions[key] ?: return@EditorDraggableControl
                                    val newX = (cur.x + dx / screenW).coerceIn(0.01f, 0.99f)
                                    val newY = (cur.y + dy / screenH).coerceIn(0.02f, 0.98f)
                                    positions = positions.toMutableMap().apply {
                                        this[key] = cur.copy(x = newX, y = newY)
                                    }
                                }
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (isControlsMode) 1.0f else 0.50f },
                                    shape = RoundedCornerShape(12.dp),
                                    color = theme.bumperColor,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(3.dp, GamepadPrimary) else null,
                                    shadowElevation = 3.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = lbl,
                                            fontSize = (15 * pos.size).toInt().sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        "btn_back", "btn_start" -> {
                            val size = (46f * pos.size).dp
                            val lbl = if (key == "btn_back") "BACK" else "START"

                            EditorDraggableControl(
                                x = pos.x,
                                y = pos.y,
                                itemWidth = size,
                                itemHeight = size,
                                screenW = screenW,
                                screenH = screenH,
                                enabled = isControlsMode,
                                onSelect = { selectedKey = key },
                                onDragStart = { pushUndoSnapshot() },
                                onDrag = { dx, dy ->
                                    val cur = positions[key] ?: return@EditorDraggableControl
                                    val newX = (cur.x + dx / screenW).coerceIn(0.01f, 0.99f)
                                    val newY = (cur.y + dy / screenH).coerceIn(0.02f, 0.98f)
                                    positions = positions.toMutableMap().apply {
                                        this[key] = cur.copy(x = newX, y = newY)
                                    }
                                }
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (isControlsMode) 1.0f else 0.50f },
                                    shape = RoundedCornerShape(10.dp),
                                    color = theme.centerColor,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(3.dp, GamepadPrimary) else null,
                                    shadowElevation = 2.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = lbl,
                                            fontSize = (9 * pos.size).toInt().sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 3. Barre d'outils supérieure flottante ───────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.90f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF232838))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = GamepadPrimary, modifier = Modifier.size(20.dp))
                    }

                    // Bouton Sélecteur / Gestion de Profil
                    OutlinedButton(
                        onClick = {
                            viewModel.refreshProfilesList()
                            showProfilesDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = TGCGold, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentProfile?.name ?: "Profil",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("▾", fontSize = 11.sp, color = TGCGold)
                    }
                }

                // Sélecteur d'onglets : Touches vs Arrière-Plan vs Tester
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1B2030))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        "controls"   to "🎮 Touches",
                        "background" to "🖼 Fond",
                        "test"       to "🧪 Tester"
                    ).forEach { (mode, label) ->
                        val isSel = editorMode == mode
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) (if (mode == "test") Color(0xFF00E676) else if (mode == "background") TGCGold else GamepadPrimary) else Color.Transparent,
                            modifier = Modifier.clickable { editorMode = mode }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Actions : Undo / Redo / Réinitialiser / Sauvegarder
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Undo
                    IconButton(
                        onClick = { performUndo() },
                        enabled = undoStack.isNotEmpty() && editorMode == "controls",
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Annuler",
                            tint = if (undoStack.isNotEmpty() && editorMode == "controls") GamepadPrimary else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Redo
                    IconButton(
                        onClick = { performRedo() },
                        enabled = redoStack.isNotEmpty() && editorMode == "controls",
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = "Rétablir",
                            tint = if (redoStack.isNotEmpty() && editorMode == "controls") GamepadPrimary else Color.DarkGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Bouton Réinitialiser
                    OutlinedButton(
                        onClick = {
                            if (editorMode == "controls") {
                                pushUndoSnapshot()
                                positions = LayoutDefaults.defaultPositions.toMutableMap()
                                Toast.makeText(context, "Touches réinitialisées par défaut", Toast.LENGTH_SHORT).show()
                            } else if (editorMode == "background") {
                                bgScale = 1.0f
                                bgOffsetX = 0.0f
                                bgOffsetY = 0.0f
                                bgDim = 0.35f
                                Toast.makeText(context, "Arrière-plan réinitialisé", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = TGCGold, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Défaut", color = TGCGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Bouton Sauvegarder
                    Button(
                        onClick = {
                            viewModel.updateAllButtonPositions(positions)
                            viewModel.applyBackgroundAdjustment(bgDim, bgScale, bgOffsetX, bgOffsetY)
                            Toast.makeText(context, "Disposition enregistrée sur '${currentProfile?.name ?: "Profil"}' !", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Sauvegarder", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // ── 4. Barre inférieure : Propriétés Touches (X, Y, Taille, Centrage) ─
        if (editorMode == "controls") {
            selectedKey?.let { key ->
                val pos = positions[key]
                if (pos != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                            .fillMaxWidth(0.88f),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.92f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GamepadPrimary.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = LayoutDefaults.controlLabels[key] ?: key,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GamepadPrimary,
                                modifier = Modifier.width(130.dp)
                            )

                            // Slider X
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("X: ${(pos.x * 100).toInt()}%", fontSize = 10.sp, color = Color.White)
                                Slider(
                                    value = pos.x,
                                    onValueChange = { newX ->
                                        positions = positions.toMutableMap().apply {
                                            this[key] = pos.copy(x = newX)
                                        }
                                    },
                                    valueRange = 0.01f..0.99f,
                                    modifier = Modifier.width(85.dp),
                                    colors = SliderDefaults.colors(thumbColor = GamepadPrimary, activeTrackColor = GamepadPrimary)
                                )
                            }

                            // Slider Y
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Y: ${(pos.y * 100).toInt()}%", fontSize = 10.sp, color = Color.White)
                                Slider(
                                    value = pos.y,
                                    onValueChange = { newY ->
                                        positions = positions.toMutableMap().apply {
                                            this[key] = pos.copy(y = newY)
                                        }
                                    },
                                    valueRange = 0.02f..0.98f,
                                    modifier = Modifier.width(85.dp),
                                    colors = SliderDefaults.colors(thumbColor = GamepadPrimary, activeTrackColor = GamepadPrimary)
                                )
                            }

                            // Slider Taille
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Taille: ${(pos.size * 100).toInt()}%", fontSize = 10.sp, color = TGCGold)
                                Slider(
                                    value = pos.size,
                                    onValueChange = { newScale ->
                                        positions = positions.toMutableMap().apply {
                                            this[key] = pos.copy(size = newScale)
                                        }
                                    },
                                    valueRange = 0.60f..1.60f,
                                    modifier = Modifier.width(85.dp),
                                    colors = SliderDefaults.colors(thumbColor = TGCGold, activeTrackColor = TGCGold)
                                )
                            }

                            // Bouton Centrer / Défaut
                            TextButton(
                                onClick = {
                                    pushUndoSnapshot()
                                    val def = LayoutDefaults.defaultPositions[key]
                                    if (def != null) {
                                        positions = positions.toMutableMap().apply {
                                            this[key] = def
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Centrer", color = TGCGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── 5. Barre inférieure : Réglages Arrière-Plan ───────────────────────
        if (editorMode == "background") {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .fillMaxWidth(0.92f),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, TGCGold.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Zoom: ${String.format("%.1f", bgScale)}x", fontSize = 10.sp, color = Color.White)
                        Slider(
                            value = bgScale,
                            onValueChange = { bgScale = it },
                            valueRange = 1.0f..5.0f,
                            modifier = Modifier.width(80.dp),
                            colors = SliderDefaults.colors(thumbColor = TGCGold, activeTrackColor = TGCGold)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pan X: ${(bgOffsetX * 100).toInt()}%", fontSize = 10.sp, color = Color.White)
                        Slider(
                            value = bgOffsetX,
                            onValueChange = { bgOffsetX = it },
                            valueRange = -0.8f..0.8f,
                            modifier = Modifier.width(75.dp),
                            colors = SliderDefaults.colors(thumbColor = GamepadPrimary, activeTrackColor = GamepadPrimary)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pan Y: ${(bgOffsetY * 100).toInt()}%", fontSize = 10.sp, color = Color.White)
                        Slider(
                            value = bgOffsetY,
                            onValueChange = { bgOffsetY = it },
                            valueRange = -0.8f..0.8f,
                            modifier = Modifier.width(75.dp),
                            colors = SliderDefaults.colors(thumbColor = GamepadPrimary, activeTrackColor = GamepadPrimary)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Dim: ${(bgDim * 100).toInt()}%", fontSize = 10.sp, color = Color.White)
                        Slider(
                            value = bgDim,
                            onValueChange = { bgDim = it },
                            valueRange = 0.0f..0.85f,
                            modifier = Modifier.width(70.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                        )
                    }

                    Button(
                        onClick = { bgPicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = GamepadCard),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("🖼 Changer", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        }
    }

    // ── 6. Dialogue Gestion Multi-Profils & Import / Export ─────────────────
    if (showProfilesDialog) {
        AlertDialog(
            onDismissRequest = { showProfilesDialog = false },
            containerColor = GamepadSurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gestion des Profils", color = GamepadPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { showNewProfileDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Nouveau Profil", tint = TGCGold)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Profil actif : ${currentProfile?.name ?: "Défaut"} (${activeProfileFilename})",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    // Liste des profils
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(profilesList) { filename ->
                            val isSelected = filename == activeProfileFilename
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) GamepadPrimary.copy(alpha = 0.2f) else GamepadCard,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, GamepadPrimary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.switchProfile(filename)
                                        // Mettre à jour les positions locales
                                        positions = LayoutDefaults.getEffectivePositions(viewModel.customLayout.value).toMutableMap()
                                        showProfilesDialog = false
                                        Toast.makeText(context, "Profil sélectionné : $filename", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = filename.removeSuffix(".json"),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) GamepadPrimary else Color.White
                                    )
                                    if (isSelected) {
                                        Text("✔ Actif", fontSize = 10.sp, color = GamepadPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Boutons d'action pour le profil courant
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.duplicateCurrentProfile("${currentProfile?.name ?: "Profil"} (Copie)") {
                                    Toast.makeText(context, "Profil dupliqué !", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = TGCGold)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Dupliquer", fontSize = 10.sp, color = TGCGold)
                        }

                        OutlinedButton(
                            onClick = { showRenameDialog = true },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Renommer", fontSize = 10.sp, color = Color.White)
                        }

                        if (activeProfileFilename != "default_profile.json") {
                            OutlinedButton(
                                onClick = {
                                    viewModel.deleteCurrentProfile { success ->
                                        if (success) {
                                            positions = LayoutDefaults.getEffectivePositions(viewModel.customLayout.value).toMutableMap()
                                            Toast.makeText(context, "Profil supprimé", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f).height(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF5252))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Suppr.", fontSize = 10.sp, color = Color(0xFFFF5252))
                            }
                        }
                    }

                    Divider(color = Color(0xFF232838), thickness = 1.dp)

                    // Export / Import .phantom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                exportLauncher.launch("${currentProfile?.name ?: "profil"}.phantom")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GamepadCard),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = GamepadPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Exporter .phantom", fontSize = 10.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                importLauncher.launch("*/*")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GamepadCard),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = TGCGold, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Importer .phantom", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfilesDialog = false }) {
                    Text("Fermer", color = GamepadPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }

    // Dialogue Nouveau Profil
    if (showNewProfileDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProfileDialog = false },
            containerColor = GamepadSurface,
            title = { Text("Nouveau Profil", color = GamepadPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nom du profil (ex: FPS, Course…)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GamepadPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.createNewProfile(newName) {
                                positions = LayoutDefaults.getEffectivePositions(viewModel.customLayout.value).toMutableMap()
                                showNewProfileDialog = false
                                Toast.makeText(context, "Profil créé !", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary)
                ) {
                    Text("Créer", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProfileDialog = false }) {
                    Text("Annuler", color = Color.Gray)
                }
            }
        )
    }

    // Dialogue Renommer Profil
    if (showRenameDialog) {
        var renameVal by remember { mutableStateOf(currentProfile?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = GamepadSurface,
            title = { Text("Renommer le Profil", color = GamepadPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameVal,
                    onValueChange = { renameVal = it },
                    label = { Text("Nouveau nom") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GamepadPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameVal.isNotBlank()) {
                            viewModel.renameCurrentProfile(renameVal) {
                                showRenameDialog = false
                                Toast.makeText(context, "Profil renommé !", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GamepadPrimary)
                ) {
                    Text("Renommer", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Annuler", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun EditorDraggableControl(
    x: Float,
    y: Float,
    itemWidth: androidx.compose.ui.unit.Dp,
    itemHeight: androidx.compose.ui.unit.Dp,
    screenW: Float,
    screenH: Float,
    enabled: Boolean = true,
    onSelect: () -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDrag: (dxDp: Float, dyDp: Float) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDragStart by rememberUpdatedState(onDragStart)

    val posX = (screenW * x - itemWidth.value / 2f).dp
    val posY = (screenH * y - itemHeight.value / 2f).dp

    Box(
        modifier = Modifier
            .offset(x = posX, y = posY)
            .size(width = itemWidth, height = itemHeight)
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                currentOnSelect()
                                currentOnDragStart?.invoke()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dxDp = with(density) { dragAmount.x.toDp().value }
                                val dyDp = with(density) { dragAmount.y.toDp().value }
                                currentOnDrag(dxDp, dyDp)
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}
