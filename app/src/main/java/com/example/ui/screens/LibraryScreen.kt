package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.DownloadedMediaEntity
import com.example.data.model.MediaFormatters
import com.example.data.model.MediaType
import com.example.data.model.SocialPlatform
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PlatformBadge
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.ErrorCoral
import java.text.DateFormat
import java.util.Date

private enum class LibraryFilter {
    ALL, VIDEO, IMAGE, AUDIO
}

private enum class LibrarySort {
    NEWEST, NAME, SIZE
}

@Composable
fun LibraryScreen(
    items: List<DownloadedMediaEntity>,
    isMediaAccessible: (DownloadedMediaEntity) -> Boolean,
    onRename: (DownloadedMediaEntity, String) -> Unit,
    onDelete: (DownloadedMediaEntity) -> Unit,
    onLinkCopied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var sort by remember { mutableStateOf(LibrarySort.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    var renameTarget by remember { mutableStateOf<DownloadedMediaEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<DownloadedMediaEntity?>(null) }
    var detailsTarget by remember { mutableStateOf<DownloadedMediaEntity?>(null) }

    val visibleItems = remember(items, searchQuery, filter, sort) {
        val byType = when (filter) {
            LibraryFilter.ALL -> items
            LibraryFilter.VIDEO -> items.filter { MediaType.fromString(it.mediaType) == MediaType.VIDEO }
            LibraryFilter.IMAGE -> items.filter { MediaType.fromString(it.mediaType) == MediaType.IMAGE }
            LibraryFilter.AUDIO -> items.filter { MediaType.fromString(it.mediaType) == MediaType.AUDIO }
        }
        val bySearch = if (searchQuery.isBlank()) byType else {
            val q = searchQuery.trim().lowercase()
            byType.filter {
                it.title.lowercase().contains(q) ||
                    it.fileName.lowercase().contains(q) ||
                    it.platformId.lowercase().contains(q)
            }
        }
        when (sort) {
            LibrarySort.NEWEST -> bySearch.sortedByDescending { it.createdAt }
            LibrarySort.NAME -> bySearch.sortedBy { it.title.lowercase() }
            LibrarySort.SIZE -> bySearch.sortedByDescending { it.fileSizeBytes }
        }
    }

    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(strings.renameTitle) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(strings.newNameLabel) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRename(target, text)
                    renameTarget = null
                }) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(strings.confirmDeleteTitle) },
            text = { Text(strings.confirmDeleteMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(target)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorCoral)
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    detailsTarget?.let { target ->
        val platform = SocialPlatform.fromId(target.platformId)
        val dateStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(target.createdAt))
        AlertDialog(
            onDismissRequest = { detailsTarget = null },
            title = { Text(strings.fileDetails) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${target.fileName}", style = MaterialTheme.typography.titleSmall)
                    Text("${platform.displayName} • ${target.mimeType}", style = MaterialTheme.typography.bodySmall)
                    Text("${strings.fileSizeLabel}: ${MediaFormatters.formatBytes(target.fileSizeBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsTarget = null }) {
                    Text(strings.close)
                }
            }
        )
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchLibraryHint) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("library_search_input")
                )
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = strings.sortNewest)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(strings.sortNewest) },
                            onClick = { sort = LibrarySort.NEWEST; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.sortName) },
                            onClick = { sort = LibrarySort.NAME; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text(strings.sortSize) },
                            onClick = { sort = LibrarySort.SIZE; showSortMenu = false }
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LibraryFilter.entries) { itemFilter ->
                    val label = when (itemFilter) {
                        LibraryFilter.ALL -> strings.allLabel
                        LibraryFilter.VIDEO -> strings.videoLabel
                        LibraryFilter.IMAGE -> strings.imageLabel
                        LibraryFilter.AUDIO -> strings.audioLabel
                    }
                    FilterChip(
                        selected = filter == itemFilter,
                        onClick = { filter = itemFilter },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (visibleItems.isEmpty()) {
                EmptyStateView(
                    title = strings.noLibraryTitle,
                    subtitle = strings.noLibrarySubtitle
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleItems, key = { it.id }) { media ->
                        LibraryMediaCard(
                            entity = media,
                            strings = strings,
                            onOpen = {
                                if (isMediaAccessible(media)) {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(media.uriString), media.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(intent, strings.openMedia)) }
                                }
                            },
                            onShare = {
                                if (isMediaAccessible(media)) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = media.mimeType
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(media.uriString))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(intent, strings.shareMedia)) }
                                }
                            },
                            onCopyLink = {
                                clipboard.setText(AnnotatedString(media.originalUrl))
                                onLinkCopied()
                            },
                            onRename = { renameTarget = media },
                            onDetails = { detailsTarget = media },
                            onDelete = { deleteTarget = media }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryMediaCard(
    entity: DownloadedMediaEntity,
    strings: AppStrings,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onRename: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val platform = SocialPlatform.fromId(entity.platformId)
    val mediaType = MediaType.fromString(entity.mediaType)
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!entity.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = entity.thumbnailUrl,
                    contentDescription = entity.title,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (mediaType) {
                            MediaType.VIDEO -> Icons.Rounded.VideoLibrary
                            MediaType.IMAGE -> Icons.Rounded.Image
                            MediaType.AUDIO -> Icons.Rounded.AudioFile
                        },
                        contentDescription = entity.mediaType,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlatformBadge(platform = platform, size = 20.dp)
                    Text(
                        text = "${platform.displayName} • ${MediaFormatters.formatBytes(entity.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = strings.openMedia)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = strings.shareMedia)
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = strings.fileDetails)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.renameMedia) },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.copyLink) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = { menuExpanded = false; onCopyLink() }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.fileDetails) },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = { menuExpanded = false; onDetails() }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.deleteMedia, color = ErrorCoral) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = ErrorCoral) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}
