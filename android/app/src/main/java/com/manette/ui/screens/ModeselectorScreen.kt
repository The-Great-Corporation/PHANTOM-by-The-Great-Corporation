package com.manette.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manette.ui.theme.GamepadAccent
import com.manette.ui.theme.GamepadBackground
import com.manette.ui.theme.GamepadPrimary
import com.manette.ui.theme.GamepadSecondary

@Composable
fun ModeSelectorScreen(
    onTheGreatModeSelected: () -> Unit,
    onPlugAndPlayModeSelected: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GamepadBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Manette",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = GamepadPrimary,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Virtual Gamepad System",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // The Great Mode Card
            ModeCard(
                title = "The Great",
                description = "Advanced PC Server Mode\n\n• Multi-connection (Wi-Fi, Bluetooth, USB)\n• Xbox 360/DualShock 4 emulation\n• Haptic feedback\n• Zero-latency USB via ADB",
                color = GamepadPrimary,
                onClick = onTheGreatModeSelected
            )
            
            // Plug & Play Mode Card
            ModeCard(
                title = "Plug & Play",
                description = "Native Bluetooth HID Mode\n\n• No server required\n• Universal compatibility\n• Instant recognition\n• Works with any host device\n• Simple setup",
                color = GamepadSecondary,
                onClick = onPlugAndPlayModeSelected
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeCard(
    title: String,
    description: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = GamepadBackground
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = description,
                fontSize = 14.sp,
                color = GamepadBackground.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}
