package com.audio.editor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audio.editor.domain.model.AudioEditSettings
import com.audio.editor.domain.model.AudioOutputFormat
import com.audio.editor.ui.theme.PrimaryIndigo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun AudioSettingsSection(
    settings: AudioEditSettings,
    onSpeedChange: (Float) -> Unit,
    onSpeedAdjustStep: (Float) -> Unit,
    onSpeedReset: () -> Unit,
    onBitrateChange: (Int) -> Unit,
    onBitrateReset: () -> Unit,
    onSampleRateChange: (Int) -> Unit,
    onSampleRateReset: () -> Unit,
    onChannelsChange: (Int) -> Unit,
    onFormatChange: (AudioOutputFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إعدادات الصوت والمعالجة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. SPEED CONTROLS (0.25x - 3.00x) with +/- 0.01 and Long Press
            SpeedControlCard(
                speed = settings.speed,
                onSpeedChange = onSpeedChange,
                onSpeedAdjustStep = onSpeedAdjustStep,
                onSpeedReset = onSpeedReset
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. BITRATE CONTROLS (56 - 320 kbps)
            BitrateControlCard(
                selectedBitrate = settings.bitrateKbps,
                onBitrateChange = onBitrateChange,
                onBitrateReset = onBitrateReset
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. SAMPLE RATE CONTROLS (8000 - 96000 Hz)
            SampleRateControlCard(
                selectedSampleRate = settings.sampleRateHz,
                onSampleRateChange = onSampleRateChange,
                onSampleRateReset = onSampleRateReset
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. CHANNELS & FORMAT
            ChannelsAndFormatCard(
                selectedChannels = settings.channels,
                selectedFormat = settings.outputFormat,
                onChannelsChange = onChannelsChange,
                onFormatChange = onFormatChange
            )
        }
    }
}

@Composable
private fun SpeedControlCard(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onSpeedAdjustStep: (Float) -> Unit,
    onSpeedReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "سرعة التشغيل", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format("%.2fx", speed),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryIndigo
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onSpeedReset) {
                    Text("استعادة 1.00x", fontSize = 11.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minus Button with Long-press Continuous Repeat
            LongPressRepeatButton(
                icon = Icons.Default.Remove,
                contentDescription = "تقليل السرعة",
                onStep = { onSpeedAdjustStep(-0.01f) }
            )

            Slider(
                value = speed,
                onValueChange = { onSpeedChange(Math.round(it * 100f) / 100f) },
                valueRange = 0.25f..3.00f,
                steps = 274,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(thumbColor = PrimaryIndigo, activeTrackColor = PrimaryIndigo)
            )

            // Plus Button with Long-press Continuous Repeat
            LongPressRepeatButton(
                icon = Icons.Default.Add,
                contentDescription = "زيادة السرعة",
                onStep = { onSpeedAdjustStep(0.01f) }
            )
        }
    }
}

@Composable
private fun LongPressRepeatButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onStep: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            // Wait initial delay of 1 second before auto-repeating
            delay(1000L)
            while (isActive && isPressed) {
                onStep()
                delay(60L) // Safe repeat interval
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(36.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onStep()
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(8.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BitrateControlCard(
    selectedBitrate: Int,
    onBitrateChange: (Int) -> Unit,
    onBitrateReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "معدل البت (Bitrate)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onBitrateReset) {
                Text("استعادة الأصلي", fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioEditSettings.SUPPORTED_BITRATES.forEach { rate ->
                FilterChip(
                    selected = selectedBitrate == rate,
                    onClick = { onBitrateChange(rate) },
                    label = { Text("$rate kbps", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryIndigo.copy(alpha = 0.15f),
                        selectedLabelColor = PrimaryIndigo
                    )
                )
            }
        }
    }
}

@Composable
private fun SampleRateControlCard(
    selectedSampleRate: Int,
    onSampleRateChange: (Int) -> Unit,
    onSampleRateReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "معدل العينة (Sample Rate)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onSampleRateReset) {
                Text("استعادة الأصلي", fontSize = 11.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioEditSettings.SUPPORTED_SAMPLE_RATES.forEach { rate ->
                FilterChip(
                    selected = selectedSampleRate == rate,
                    onClick = { onSampleRateChange(rate) },
                    label = { Text("$rate Hz", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryIndigo.copy(alpha = 0.15f),
                        selectedLabelColor = PrimaryIndigo
                    )
                )
            }
        }
    }
}

@Composable
private fun ChannelsAndFormatCard(
    selectedChannels: Int,
    selectedFormat: AudioOutputFormat,
    onChannelsChange: (Int) -> Unit,
    onFormatChange: (AudioOutputFormat) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Channels
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "القنوات الصوتية", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedChannels == 1,
                    onClick = { onChannelsChange(1) },
                    label = { Text("Mono (1)", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedChannels == 2,
                    onClick = { onChannelsChange(2) },
                    label = { Text("Stereo (2)", fontSize = 11.sp) }
                )
            }
        }

        // Format
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "صيغة الإخراج", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AudioOutputFormat.values().forEach { fmt ->
                    FilterChip(
                        selected = selectedFormat == fmt,
                        onClick = { onFormatChange(fmt) },
                        label = { Text(fmt.extension.uppercase(), fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}
