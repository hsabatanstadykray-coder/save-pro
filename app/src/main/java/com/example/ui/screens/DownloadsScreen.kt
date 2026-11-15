package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.MediaFormatters
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PlatformBadge
import com.example.ui.components.StatusPill
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber

private enum class DownloadFilter {
    ALL, ACTIVE, COMPLETED, FAILED
}

@Composable
fun DownloadsScreen(
    tasks: List<DownloadTask>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var selectedFilter by remember { mutableStateOf(DownloadFilter.ALL) }

    val filtered = remember(tasks, selectedFilter) {
        when (selectedFilter) {
            DownloadFilter.ALL -> tasks
            DownloadFilter.ACTIVE -> tasks.filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED
            }
            DownloadFilter.COMPLETED -> tasks.filter { it.status == DownloadStatus.COMPLETED }
            DownloadFilter.FAILED -> tasks.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DownloadFilter.entries) { filter ->
                    val label = when (filter) {
                        DownloadFilter.ALL -> strings.allLabel
                        DownloadFilter.ACTIVE -> strings.activeLabel
                        DownloadFilter.COMPLETED -> strings.completedLabel
                        DownloadFilter.FAILED -> strings.failedLabel
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(label) }
                    )
                }
            }

            if (filtered.isEmpty()) {
                EmptyStateView(
                    title = strings.noDownloadsTitle,
                    subtitle = strings.noDownloadsSubtitle
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            strings = strings,
                            onPause = { onPause(task.id) },
                            onResume = { onResume(task.id) },
                            onRetry = { onRetry(task.id) },
                            onCancel = { onCancel(task.id) },
                            onDismiss = { onDismiss(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    strings: AppStrings,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val statusColor = when (task.status) {
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> WarningAmber
        DownloadStatus.COMPLETED -> SuccessEmerald
        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> ErrorCoral
    }

    val statusText = when (task.status) {
        DownloadStatus.QUEUED -> strings.activeLabel
        DownloadStatus.DOWNLOADING -> strings.activeLabel
        DownloadStatus.PAUSED -> strings.pause
        DownloadStatus.COMPLETED -> strings.completedLabel
        DownloadStatus.FAILED -> strings.failedLabel
        DownloadStatus.CANCELLED -> strings.cancel
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!task.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = task.thumbnailUrl,
                        contentDescription = task.title,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    PlatformBadge(platform = task.platform, size = 48.dp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPill(label = statusText, color = statusColor)
                        Text(
                            text = "${task.platform.displayName} • ${task.qualityLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (task.status == DownloadStatus.COMPLETED || task.status == DownloadStatus.CANCELLED) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = strings.dismiss)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                if (task.totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val downloadedStr = MediaFormatters.formatBytes(task.bytesDownloaded)
                val totalStr = if (task.totalBytes > 0L) " / ${MediaFormatters.formatBytes(task.totalBytes)}" else ""
                val speedStr = if (task.speedBytesPerSec > 0L) " • ${MediaFormatters.formatSpeed(task.speedBytesPerSec)}" else ""
                Text(
                    text = "$downloadedStr$totalStr$speedStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (task.status) {
                        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                            OutlinedButton(
                                onClick = onPause,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("pause_button_${task.id}")
                            ) {
                                Icon(Icons.Rounded.Pause, contentDescription = strings.pause, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.pause)
                            }
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Rounded.Cancel, contentDescription = strings.cancel, tint = ErrorCoral)
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            OutlinedButton(
                                onClick = onResume,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = strings.resume, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.resume)
                            }
                            IconButton(onClick = onCancel) {
                                Icon(Icons.Rounded.Cancel, contentDescription = strings.cancel, tint = ErrorCoral)
                            }
                        }
                        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                            OutlinedButton(
                                onClick = onRetry,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = strings.retry, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.retry)
                            }
                        }
                        DownloadStatus.COMPLETED -> Unit
                    }
                }
            }

            if (task.status == DownloadStatus.FAILED && !task.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorCoral
                )
            }
        }
    }
}
