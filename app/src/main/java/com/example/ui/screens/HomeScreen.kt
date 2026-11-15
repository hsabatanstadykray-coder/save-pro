package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.DownloadedMediaEntity
import com.example.data.model.MediaFormatters
import com.example.data.model.MediaType
import com.example.data.model.ProviderUsageInfo
import com.example.data.model.SocialPlatform
import com.example.data.network.ResolveOutcome
import com.example.ui.components.PlatformBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ResolveUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    urlInput: String,
    detectedPlatform: SocialPlatform?,
    resolveState: ResolveUiState,
    clipboardCandidateUrl: String?,
    savedCount: Int,
    activeDownloadsCount: Int,
    usageInfo: ProviderUsageInfo,
    recentMedia: List<DownloadedMediaEntity>,
    onUrlChange: (String) -> Unit,
    onPasteClick: (String?) -> Unit,
    onClearClick: () -> Unit,
    onAnalyzeClick: () -> Unit,
    onAcceptClipboard: () -> Unit,
    onDismissClipboard: () -> Unit,
    onSelectOption: (String) -> Unit,
    onStartDownload: () -> Unit,
    onDismissResolveSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    if (resolveState is ResolveUiState.Success) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissResolveSheet,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ResolveResultSheetContent(
                state = resolveState,
                strings = strings,
                onSelectOption = onSelectOption,
                onStartDownload = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartDownload()
                },
                onDismiss = onDismissResolveSheet
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroHeaderCard(
                    strings = strings,
                    onOpenSettings = onOpenSettings
                )
            }

            item {
                AnimatedVisibility(
                    visible = clipboardCandidateUrl != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    clipboardCandidateUrl?.let { candidate ->
                        ClipboardPromptCard(
                            url = candidate,
                            strings = strings,
                            onUse = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAcceptClipboard()
                            },
                            onDismiss = onDismissClipboard
                        )
                    }
                }
            }

            item {
                UrlInputCard(
                    urlInput = urlInput,
                    detectedPlatform = detectedPlatform,
                    isLoading = resolveState is ResolveUiState.Loading,
                    strings = strings,
                    onUrlChange = onUrlChange,
                    onPasteClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val text = clipboardManager.getText()?.text
                        onPasteClick(text)
                    },
                    onClearClick = onClearClick,
                    onAnalyzeClick = {
                        keyboardController?.hide()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAnalyzeClick()
                    }
                )
            }

            if (resolveState is ResolveUiState.Loading) {
                item {
                    LoadingSkeletonCard(strings = strings)
                }
            }

            if (resolveState is ResolveUiState.Error) {
                item {
                    ErrorNoticeCard(
                        error = resolveState.error,
                        strings = strings,
                        onOpenSettings = onOpenSettings
                    )
                }
            }

            item {
                StatsRow(
                    savedCount = savedCount,
                    activeCount = activeDownloadsCount,
                    usageInfo = usageInfo,
                    strings = strings,
                    onSavedClick = onOpenLibrary,
                    onActiveClick = onOpenDownloads,
                    onCreditsClick = onOpenSettings
                )
            }

            item {
                SectionHeader(title = strings.supportedPlatforms)
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4
                ) {
                    SocialPlatform.supportedList.forEach { platform ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (detectedPlatform == platform) platform.accentColor
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PlatformBadge(platform = platform, size = 28.dp)
                                Text(
                                    text = platform.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (recentMedia.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = strings.recentDownloads,
                        trailing = {
                            TextButton(onClick = onOpenLibrary) {
                                Text(strings.allLabel)
                            }
                        }
                    )
                }
                items(recentMedia.take(4), key = { it.id }) { item ->
                    RecentMediaRow(
                        item = item,
                        onClick = onOpenLibrary
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HeroHeaderCard(
    strings: AppStrings,
    onOpenSettings: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = strings.appName,
                modifier = Modifier
                    .matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.36f
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                            )
                        )
                    )
            )
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = strings.appName,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = strings.quickSettings,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings.greetingTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.greetingSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClipboardPromptCard(
    url: String,
    strings: AppStrings,
    onUse: () -> Unit,
    onDismiss: () -> Unit
) {
    val platform = SocialPlatform.detectFromUrl(url) ?: SocialPlatform.OTHER
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlatformBadge(platform = platform, size = 34.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.clipboardDetectedTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onDismiss) {
                Text(strings.dismiss)
            }
            Button(
                onClick = onUse,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("clipboard_use_button")
            ) {
                Text(strings.clipboardUseLink)
            }
        }
    }
}

@Composable
private fun UrlInputCard(
    urlInput: String,
    detectedPlatform: SocialPlatform?,
    isLoading: Boolean,
    strings: AppStrings,
    onUrlChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onClearClick: () -> Unit,
    onAnalyzeClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (detectedPlatform != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlatformBadge(platform = detectedPlatform, size = 28.dp)
                        Text(
                            text = "${strings.detectedPlatform}: ${detectedPlatform.displayName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = detectedPlatform.accentColor
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = strings.unknownPlatform,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_input_field"),
                placeholder = {
                    Text(
                        text = strings.pasteLinkHint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onAnalyzeClick() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                trailingIcon = {
                    if (urlInput.isNotEmpty()) {
                        IconButton(
                            onClick = onClearClick,
                            modifier = Modifier.testTag("clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = strings.clearInput
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPasteClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("paste_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = strings.pasteFromClipboard,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.pasteFromClipboard)
                }

                Button(
                    onClick = onAnalyzeClick,
                    enabled = !isLoading && urlInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("fetch_media_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.analyzingLink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = strings.analyzeAndDownload,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.analyzeAndDownload, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.publicContentDisclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingSkeletonCard(strings: AppStrings) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = strings.analyzingLink,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
private fun ErrorNoticeCard(
    error: ResolveOutcome.Error,
    strings: AppStrings,
    onOpenSettings: () -> Unit
) {
    val message = when (error) {
        ResolveOutcome.Error.MissingApiKey -> strings.apiMissingKeyHelp
        ResolveOutcome.Error.InvalidUrl -> strings.invalidUrlError
        ResolveOutcome.Error.DuplicateRequest -> strings.duplicateRequestToast
        is ResolveOutcome.Error.UnsupportedPlatform -> error.detail?.takeIf { it.isNotBlank() } ?: strings.unsupportedPlatformError
        is ResolveOutcome.Error.NoMediaFound -> error.detail?.takeIf { it.isNotBlank() } ?: strings.noMediaFoundError
        ResolveOutcome.Error.RateLimited -> strings.rateLimitError
        ResolveOutcome.Error.Timeout -> strings.timeoutError
        ResolveOutcome.Error.NetworkError -> strings.networkError
        is ResolveOutcome.Error.ApiError -> error.message
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (error is ResolveOutcome.Error.MissingApiKey) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onOpenSettings,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(strings.settingsApiStatus)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(
    savedCount: Int,
    activeCount: Int,
    usageInfo: ProviderUsageInfo,
    strings: AppStrings,
    onSavedClick: () -> Unit,
    onActiveClick: () -> Unit,
    onCreditsClick: () -> Unit
) {
    val creditsText = when {
        !usageInfo.isKeyConfigured -> "—"
        usageInfo.creditsRemaining != null -> String.format(Locale.US, "%.1f", usageInfo.creditsRemaining)
        else -> "…"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            value = savedCount.toString(),
            label = strings.totalSaved,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            onClick = onSavedClick
        )
        StatCard(
            value = activeCount.toString(),
            label = strings.activeQueue,
            accent = if (activeCount > 0) WarningAmber else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
            onClick = onActiveClick
        )
        StatCard(
            value = creditsText,
            label = strings.creditsRemaining,
            accent = if (usageInfo.isConnected) SuccessEmerald else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
            onClick = onCreditsClick
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentMediaRow(
    item: DownloadedMediaEntity,
    onClick: () -> Unit
) {
    val platform = SocialPlatform.fromId(item.platformId)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlatformBadge(platform = platform, size = 38.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${platform.displayName} • ${MediaFormatters.formatBytes(item.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = when (MediaType.fromString(item.mediaType)) {
                    MediaType.VIDEO -> Icons.Rounded.VideoLibrary
                    MediaType.IMAGE -> Icons.Rounded.Image
                    MediaType.AUDIO -> Icons.Rounded.AudioFile
                },
                contentDescription = item.mediaType,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolveResultSheetContent(
    state: ResolveUiState.Success,
    strings: AppStrings,
    onSelectOption: (String) -> Unit,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val result = state.result
    val selectedOption = result.options.firstOrNull { it.id == state.selectedOptionId }
        ?: result.options.first()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!result.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = result.title,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                PlatformBadge(platform = result.platform, size = 56.dp)
            }

            Column(modifier = Modifier.weight(1f)) {
                StatusPill(label = result.platform.displayName, color = result.platform.accentColor)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                result.author?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val dur = MediaFormatters.formatDuration(result.durationSeconds)
            if (dur.isNotEmpty()) {
                StatusPill(label = "${strings.durationLabel}: $dur", color = MaterialTheme.colorScheme.secondary)
            }
            selectedOption.fileSizeBytes?.let { size ->
                StatusPill(
                    label = "${strings.fileSizeLabel}: ${MediaFormatters.formatBytes(size)}",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            StatusPill(
                label = when (selectedOption.type) {
                    MediaType.VIDEO -> strings.videoLabel
                    MediaType.IMAGE -> strings.imageLabel
                    MediaType.AUDIO -> strings.audioLabel
                },
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        Text(
            text = strings.selectQualityFormat,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            result.options.forEach { option ->
                val isSelected = option.id == selectedOption.id
                val typeTag = when (option.type) {
                    MediaType.VIDEO -> strings.videoLabel
                    MediaType.IMAGE -> strings.imageLabel
                    MediaType.AUDIO -> strings.audioLabel
                }
                val sizeStr = option.fileSizeBytes?.let { " • ${MediaFormatters.formatBytes(it)}" } ?: ""
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectOption(option.id) },
                    label = {
                        Text("$typeTag • ${option.qualityLabel}$sizeStr")
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(50.dp)
            ) {
                Text(strings.cancel)
            }
            Button(
                onClick = onStartDownload,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("start_download_button")
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.startDownload)
            }
        }
    }
}
