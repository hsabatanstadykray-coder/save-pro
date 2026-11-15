package com.example.data.network

import com.example.data.model.MediaResolveResult
import com.example.data.model.ProviderUsageInfo

sealed class ResolveOutcome {
    data class Success(val result: MediaResolveResult) : ResolveOutcome()
    sealed class Error : ResolveOutcome() {
        data object MissingApiKey : Error()
        data object InvalidUrl : Error()
        data object DuplicateRequest : Error()
        data class UnsupportedPlatform(val detail: String? = null) : Error()
        data class NoMediaFound(val detail: String? = null) : Error()
        data object RateLimited : Error()
        data object Timeout : Error()
        data object NetworkError : Error()
        data class ApiError(val statusCode: Int, val message: String) : Error()
    }
}

interface MediaDownloaderProvider {
    val providerName: String
    fun isConfigured(): Boolean
    suspend fun resolveMedia(url: String): ResolveOutcome
    suspend fun fetchUsageInfo(): ProviderUsageInfo
}
