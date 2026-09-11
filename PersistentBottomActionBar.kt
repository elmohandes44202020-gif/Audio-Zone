package com.audio.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audio.editor.domain.model.AudioProcessingState
import com.audio.editor.ui.theme.AccentEmerald
import com.audio.editor.ui.theme.ErrorRose
import com.audio.editor.ui.theme.PrimaryIndigo

@Composable
fun PersistentBottomActionBar(
    fileCount: Int,
    processingState: AudioProcessingState,
    onStartBatch: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isProcessing = processingState is AudioProcessingState.ProcessingBatch || processingState is AudioProcessingState.Preparing
    val canStart = fileCount > 0 && !isProcessing

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Active Batch Linear Progress Indicator
            if (processingState is AudioProcessingState.ProcessingBatch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "جاري معالجة: ${processingState.currentFileName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "${processingState.overallProgressPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryIndigo
                    )
                }

                LinearProgressIndicator(
                    progress = { processingState.overallProgressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = PrimaryIndigo,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info Summary Badge
                Column {
                    Text(
                        text = if (fileCount > 0) "$fileCount ملفات صوتية جاهزة" else "لم يتم اختيار ملفات",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (fileCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                    )
                    if (isProcessing) {
                        Text(
                            text = "⚡ جاري المعالجة المتزامنة...",
                            fontSize = 11.sp,
                            color = AccentEmerald,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProcessing) {
                        OutlinedButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRose),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إلغاء", fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = onStartBatch,
                            enabled = canStart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryIndigo,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (fileCount > 1) "بدء معالجة الدفعة ($fileCount)" else "بدء المعالجة",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
