package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.DownloadEvent
import com.example.data.local.AppLanguage
import com.example.data.local.DefaultQuality
import com.example.data.local.DownloadHistoryEntity
import com.example.data.local.DownloadedMediaEntity
import com.example.data.local.SaveLocation
import com.example.data.local.ThemeMode
import com.example.data.local.UserSettings
import com.example.data.model.DownloadTask
import com.example.data.model.MediaOption
import com.example.data.model.MediaResolveResult
import com.example.data.model.MediaType
import com.example.data.model.ProviderUsageInfo
import com.example.data.model.SocialPlatform
import com.example.data.network.ResolveOutcome
import com.example.data.repository.DownloaderRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ResolveUiState {
    data object Idle : ResolveUiState()
    data object Loading : ResolveUiState()
    data class Success(
        val result: MediaResolveResult,
        val selectedOptionId: String
    ) : ResolveUiState()
    data class Error(val error: ResolveOutcome.Error) : ResolveUiState()
}

sealed class UiToast {
    data object DownloadStarted : UiToast()
    data class DownloadCompleted(val title: String) : UiToast()
    data class DownloadFailed(val reason: String) : UiToast()
    data object LinkCopied : UiToast()
    data object CacheCleared : UiToast()
    data object HistoryCleared : UiToast()
    data object ClipboardEmpty : UiToast()
    data object DuplicateRequest : UiToast()
    data object FileMissing : UiToast()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DownloaderRepository(application.applicationContext)

    val settings: StateFlow<UserSettings> = repository.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UserSettings()
    )

    val downloadTasks: StateFlow<List<DownloadTask>> = repository.downloadTasks

    val libraryItems: StateFlow<List<DownloadedMediaEntity>> = repository.libraryFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val historyItems: StateFlow<List<DownloadHistoryEntity>> = repository.historyFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val savedCount: StateFlow<Int> = repository.savedCountFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _detectedPlatform = MutableStateFlow<SocialPlatform?>(null)
    val detectedPlatform: StateFlow<SocialPlatform?> = _detectedPlatform.asStateFlow()

    private val _resolveUiState = MutableStateFlow<ResolveUiState>(ResolveUiState.Idle)
    val resolveUiState: StateFlow<ResolveUiState> = _resolveUiState.asStateFlow()

    private val _clipboardCandidateUrl = MutableStateFlow<String?>(null)
    val clipboardCandidateUrl: StateFlow<String?> = _clipboardCandidateUrl.asStateFlow()

    private val _usageInfo = MutableStateFlow(
        ProviderUsageInfo(
            isKeyConfigured = repository.isApiConfigured(),
            isConnected = false
        )
    )
    val usageInfo: StateFlow<ProviderUsageInfo> = _usageInfo.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _toastFlow = MutableSharedFlow<UiToast>(extraBufferCapacity = 16)
    val toastFlow: SharedFlow<UiToast> = _toastFlow.asSharedFlow()

    init {
        refreshUsageInfo()
        refreshCacheSize()
        viewModelScope.launch {
            repository.downloadEvents.collect { event ->
                when (event) {
                    is DownloadEvent.Completed -> _toastFlow.tryEmit(UiToast.DownloadCompleted(event.title))
                    is DownloadEvent.Failed -> _toastFlow.tryEmit(UiToast.DownloadFailed(event.reason))
                }
                refreshCacheSize()
            }
        }
    }

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
        _detectedPlatform.value = SocialPlatform.detectFromUrl(newUrl)
        if (newUrl.isBlank() && _resolveUiState.value is ResolveUiState.Error) {
            _resolveUiState.value = ResolveUiState.Idle
        }
    }

    fun clearUrlInput() {
        _urlInput.value = ""
        _detectedPlatform.value = null
        _resolveUiState.value = ResolveUiState.Idle
    }

    fun inspectClipboardText(rawText: String?) {
        if (!settings.value.autoClipboardEnabled) {
            _clipboardCandidateUrl.value = null
            return
        }
        val validUrl = rawText?.let { SocialPlatform.extractValidHttpsUrl(it) }
        val platform = validUrl?.let { SocialPlatform.detectFromUrl(it) }
        if (validUrl != null && platform != null && validUrl != _urlInput.value.trim()) {
            _clipboardCandidateUrl.value = validUrl
        }
    }

    fun acceptClipboardCandidate() {
        val url = _clipboardCandidateUrl.value ?: return
        _clipboardCandidateUrl.value = null
        onUrlChanged(url)
    }

    fun dismissClipboardCandidate() {
        _clipboardCandidateUrl.value = null
    }

    fun pasteUrlDirect(rawText: String?) {
        val extracted = rawText?.let { SocialPlatform.extractValidHttpsUrl(it) } ?: rawText?.trim()
        if (extracted.isNullOrBlank()) {
            _toastFlow.tryEmit(UiToast.ClipboardEmpty)
            return
        }
        _clipboardCandidateUrl.value = null
        onUrlChanged(extracted)
    }

    fun handleSharedIncomingUrl(sharedText: String) {
        val extracted = SocialPlatform.extractValidHttpsUrl(sharedText) ?: return
        onUrlChanged(extracted)
        analyzeUrl()
    }

    fun analyzeUrl(overrideUrl: String? = null) {
        val targetRaw = overrideUrl ?: _urlInput.value
        if (overrideUrl != null) {
            onUrlChanged(overrideUrl)
        }
        val validUrl = SocialPlatform.extractValidHttpsUrl(targetRaw)
        if (validUrl == null) {
            _resolveUiState.value = ResolveUiState.Error(ResolveOutcome.Error.InvalidUrl)
            return
        }
        if (_resolveUiState.value is ResolveUiState.Loading) {
            _toastFlow.tryEmit(UiToast.DuplicateRequest)
            return
        }

        _resolveUiState.value = ResolveUiState.Loading
        viewModelScope.launch {
            when (val outcome = repository.resolveUrl(validUrl)) {
                is ResolveOutcome.Success -> {
                    val preferred = pickPreferredOption(outcome.result.options, settings.value.defaultQuality)
                    _resolveUiState.value = ResolveUiState.Success(
                        result = outcome.result,
                        selectedOptionId = preferred.id
                    )
                    outcome.result.creditsRemaining?.let { rem ->
                        _usageInfo.value = _usageInfo.value.copy(
                            isConnected = true,
                            creditsRemaining = rem
                        )
                    }
                }
                is ResolveOutcome.Error.DuplicateRequest -> {
                    _toastFlow.tryEmit(UiToast.DuplicateRequest)
                    _resolveUiState.value = ResolveUiState.Idle
                }
                is ResolveOutcome.Error -> {
                    _resolveUiState.value = ResolveUiState.Error(outcome)
                }
            }
        }
    }

    fun selectMediaOption(optionId: String) {
        val current = _resolveUiState.value as? ResolveUiState.Success ?: return
        _resolveUiState.value = current.copy(selectedOptionId = optionId)
    }

    fun dismissResolveSheet() {
        _resolveUiState.value = ResolveUiState.Idle
    }

    fun startSelectedDownload() {
        val state = _resolveUiState.value as? ResolveUiState.Success ?: return
        val option = state.result.options.firstOrNull { it.id == state.selectedOptionId }
            ?: state.result.options.firstOrNull()
            ?: return

        repository.enqueueDownload(state.result, option)
        _resolveUiState.value = ResolveUiState.Idle
        _toastFlow.tryEmit(UiToast.DownloadStarted)
    }

    fun pauseDownload(taskId: String) = repository.pauseDownload(taskId)
    fun resumeDownload(taskId: String) = repository.resumeDownload(taskId)
    fun retryDownload(taskId: String) = repository.retryDownload(taskId)
    fun cancelDownload(taskId: String) = repository.cancelDownload(taskId)
    fun dismissDownloadTask(taskId: String) = repository.dismissTask(taskId)

    fun renameMedia(entity: DownloadedMediaEntity, newTitle: String) {
        viewModelScope.launch {
            repository.renameMedia(entity, newTitle)
        }
    }

    fun deleteMedia(entity: DownloadedMediaEntity) {
        viewModelScope.launch {
            repository.deleteMedia(entity)
        }
    }

    fun isMediaAccessible(entity: DownloadedMediaEntity): Boolean {
        val ok = repository.isMediaUriAccessible(entity)
        if (!ok) {
            _toastFlow.tryEmit(UiToast.FileMissing)
        }
        return ok
    }

    fun notifyLinkCopied() {
        _toastFlow.tryEmit(UiToast.LinkCopied)
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch { repository.deleteHistoryItem(id) }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _toastFlow.tryEmit(UiToast.HistoryCleared)
        }
    }

    fun refreshUsageInfo() {
        viewModelScope.launch {
            _usageInfo.value = repository.fetchApiUsage()
        }
    }

    fun refreshCacheSize() {
        _cacheSizeBytes.value = repository.getCacheSizeBytes()
    }

    fun clearCache() {
        repository.clearCache()
        refreshCacheSize()
        _toastFlow.tryEmit(UiToast.CacheCleared)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setDefaultQuality(quality: DefaultQuality) {
        viewModelScope.launch { repository.setDefaultQuality(quality) }
    }

    fun setSaveLocation(location: SaveLocation) {
        viewModelScope.launch { repository.setSaveLocation(location) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    }

    fun setAutoClipboardEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoClipboardEnabled(enabled) }
    }

    private fun pickPreferredOption(options: List<MediaOption>, pref: DefaultQuality): MediaOption {
        return when (pref) {
            DefaultQuality.AUDIO_ONLY -> options.firstOrNull { it.type == MediaType.AUDIO } ?: options.first()
            DefaultQuality.HD_1080 -> options.firstOrNull { it.qualityLabel.contains("1080") || (it.height ?: 0) >= 1080 } ?: options.first()
            DefaultQuality.HD_720 -> options.firstOrNull { it.qualityLabel.contains("720") || it.height == 720 } ?: options.first()
            DefaultQuality.SD_480 -> options.firstOrNull { it.qualityLabel.contains("480") || it.height == 480 } ?: options.first()
            DefaultQuality.BEST -> options.maxByOrNull { (it.height ?: 0) * 1000L + (if (it.type == MediaType.VIDEO) 500L else 0L) } ?: options.first()
        }
    }
}
