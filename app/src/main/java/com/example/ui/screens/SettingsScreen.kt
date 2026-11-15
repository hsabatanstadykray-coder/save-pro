package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AppLanguage
import com.example.data.local.DefaultQuality
import com.example.data.local.SaveLocation
import com.example.data.local.ThemeMode
import com.example.data.local.UserSettings
import com.example.data.model.MediaFormatters
import com.example.data.model.ProviderUsageInfo
import com.example.ui.components.StatusPill
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    usageInfo: ProviderUsageInfo,
    cacheSizeBytes: Long,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onQualityChange: (DefaultQuality) -> Unit,
    onSaveLocationChange: (SaveLocation) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onAutoClipboardChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
    onRefreshUsage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onNotificationsChange(granted)
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(strings.privacyPolicy) },
            text = { Text(strings.privacyPolicyBody) },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text(strings.close) }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(strings.aboutApp) },
            text = { Text(strings.aboutAppBody) },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text(strings.close) }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(strings.clearHistory) },
            text = { Text(strings.confirmDeleteMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorCoral)
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text(strings.cancel) }
            }
        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // SaveAPI Status & Usage
            item {
                SettingsGroupCard(title = strings.settingsApiStatus, icon = Icons.Rounded.Api) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (label, color) = when {
                            !usageInfo.isKeyConfigured -> strings.apiMissingKey to WarningAmber
                            usageInfo.isConnected -> strings.apiConfigured to SuccessEmerald
                            else -> (usageInfo.statusMessage ?: strings.apiMissingKey) to ErrorCoral
                        }
                        StatusPill(label = label, color = color)
                        IconButton(
                            onClick = onRefreshUsage,
                            modifier = Modifier.testTag("refresh_usage_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = strings.refreshApiStatus)
                        }
                    }

                    if (!usageInfo.isKeyConfigured) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.apiMissingKeyHelp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.creditsRemaining,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = usageInfo.creditsRemaining?.let {
                                        String.format(Locale.US, "%.1f", it)
                                    } ?: "—",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.totalRequestsLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = usageInfo.totalRequests?.toString() ?: "—",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.rateLimitLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = usageInfo.planName ?: usageInfo.rateLimitDescription ?: "Standard",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }

            // Appearance
            item {
                SettingsGroupCard(title = strings.settingsAppearance, icon = Icons.Rounded.Palette) {
                    Text(
                        text = strings.themeModeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val modes = listOf(
                        ThemeMode.SYSTEM to strings.themeSystem,
                        ThemeMode.DARK to strings.themeDark,
                        ThemeMode.LIGHT to strings.themeLight
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                modifier = Modifier.testTag("theme_btn_${mode.name.lowercase()}")
                            ) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Language
            item {
                SettingsGroupCard(title = strings.settingsLanguage, icon = Icons.Rounded.Language) {
                    val langs = listOf(
                        AppLanguage.ENGLISH to strings.langEnglish,
                        AppLanguage.ARABIC to strings.langArabic
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        langs.forEachIndexed { index, (lang, label) ->
                            SegmentedButton(
                                selected = settings.language == lang,
                                onClick = { onLanguageChange(lang) },
                                shape = SegmentedButtonDefaults.itemShape(index, langs.size),
                                modifier = Modifier.testTag("lang_btn_${lang.code}")
                            ) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Download Preferences
            item {
                SettingsGroupCard(title = strings.settingsDownloads, icon = Icons.Rounded.SaveAlt) {
                    Text(
                        text = strings.defaultQualityLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DefaultQuality.entries.forEach { quality ->
                            FilterChip(
                                selected = settings.defaultQuality == quality,
                                onClick = { onQualityChange(quality) },
                                label = { Text(quality.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.saveLocationLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = settings.saveLocation == SaveLocation.MEDIA_STORE,
                            onClick = { onSaveLocationChange(SaveLocation.MEDIA_STORE) },
                            label = { Text(strings.saveLocationMediaStore) }
                        )
                        FilterChip(
                            selected = settings.saveLocation == SaveLocation.APP_PRIVATE,
                            onClick = { onSaveLocationChange(SaveLocation.APP_PRIVATE) },
                            label = { Text(strings.saveLocationAppPrivate) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = strings.notificationsLabel,
                        subtitle = strings.notificationsSubtitle,
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onNotificationsChange(enable)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsSwitchRow(
                        title = strings.autoClipboardLabel,
                        subtitle = strings.autoClipboardSubtitle,
                        checked = settings.autoClipboardEnabled,
                        onCheckedChange = onAutoClipboardChange
                    )
                }
            }

            // Storage & Data
            item {
                SettingsGroupCard(title = strings.settingsStorage, icon = Icons.Rounded.CleaningServices) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.clearCache, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = String.format(strings.cacheSizeFormat, MediaFormatters.formatBytes(cacheSizeBytes)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onClearCache) {
                            Text(strings.clearCache)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.clearHistory, style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(
                            onClick = { showClearHistoryDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorCoral)
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.delete)
                        }
                    }
                }
            }

            // Legal & About
            item {
                SettingsGroupCard(title = strings.settingsAboutLegal, icon = Icons.Rounded.Notifications) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPrivacyDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.privacyPolicy, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showAboutDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.aboutApp, maxLines = 1)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
