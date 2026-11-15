package com.example.data.network

import com.example.BuildConfig
import com.example.data.model.MediaOption
import com.example.data.model.MediaResolveResult
import com.example.data.model.MediaType
import com.example.data.model.ProviderUsageInfo
import com.example.data.model.SocialPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface SaveApiEndpoints {
    @GET("download")
    suspend fun resolveDownload(@Query("url") url: String): Response<ResponseBody>

    @GET("me")
    suspend fun getAccountInfo(): Response<ResponseBody>

    @GET("usage")
    suspend fun getUsageStats(): Response<ResponseBody>
}

class SaveApiProvider : MediaDownloaderProvider {
    override val providerName: String = "SaveAPI"

    private val inFlightUrls = ConcurrentHashMap.newKeySet<String>()

    private fun readApiKey(): String {
        val key = BuildConfig.SAVEAPI_KEY.trim()
        return if (key.isBlank() || key == "YOUR_SAVEAPI_KEY" || key == "null") "" else key
    }

    override fun isConfigured(): Boolean = readApiKey().isNotEmpty()

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val apiKey = readApiKey()
                val reqBuilder = chain.request().newBuilder()
                    .header("Accept", "application/json")
                if (apiKey.isNotEmpty()) {
                    reqBuilder.header("Authorization", "Bearer $apiKey")
                }
                chain.proceed(reqBuilder.build())
            }
            .addInterceptor(logging)
            .build()
    }

    private val api: SaveApiEndpoints by lazy {
        val baseUrl = BuildConfig.SAVEAPI_BASE_URL.let {
            if (it.endsWith("/")) it else "$it/"
        }
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .build()
            .create(SaveApiEndpoints::class.java)
    }

    override suspend fun resolveMedia(url: String): ResolveOutcome = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext ResolveOutcome.Error.MissingApiKey

        val cleanUrl = SocialPlatform.extractValidHttpsUrl(url)
            ?: return@withContext ResolveOutcome.Error.InvalidUrl

        if (!inFlightUrls.add(cleanUrl)) {
            return@withContext ResolveOutcome.Error.DuplicateRequest
        }

        try {
            val response = api.resolveDownload(cleanUrl)
            val bodyString = response.body()?.string() ?: response.errorBody()?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext mapHttpError(response.code(), bodyString)
            }

            parseDownloadResponse(cleanUrl, bodyString)
        } catch (_: SocketTimeoutException) {
            ResolveOutcome.Error.Timeout
        } catch (_: IOException) {
            ResolveOutcome.Error.NetworkError
        } catch (e: Exception) {
            ResolveOutcome.Error.ApiError(500, e.localizedMessage ?: "Unexpected parsing error")
        } finally {
            inFlightUrls.remove(cleanUrl)
        }
    }

    override suspend fun fetchUsageInfo(): ProviderUsageInfo = withContext(Dispatchers.IO) {
        if (!isConfigured()) {
            return@withContext ProviderUsageInfo(
                isKeyConfigured = false,
                isConnected = false,
                statusMessage = "Missing SAVEAPI_KEY"
            )
        }

        try {
            val meResp = api.getAccountInfo()
            val meBody = meResp.body()?.string() ?: meResp.errorBody()?.string().orEmpty()
            if (!meResp.isSuccessful) {
                val msg = extractErrorMessage(meBody) ?: "HTTP ${meResp.code()}"
                return@withContext ProviderUsageInfo(
                    isKeyConfigured = true,
                    isConnected = false,
                    statusMessage = msg
                )
            }

            val meJson = runCatching { JSONObject(meBody) }.getOrNull()
            val creditsObj = meJson?.optJSONObject("credits") ?: meJson?.optJSONObject("data")?.optJSONObject("credits")
            val remaining = creditsObj?.optDoubleOrNull("remaining")
                ?: meJson?.optDoubleOrNull("credits_remaining")
                ?: meJson?.optDoubleOrNull("remaining_credits")
                ?: meJson?.optDoubleOrNull("credits")
                ?: meJson?.optJSONObject("data")?.optDoubleOrNull("credits")

            val plan = meJson?.optStringNonBlank("plan")
                ?: meJson?.optJSONObject("data")?.optStringNonBlank("plan")
                ?: meJson?.optStringNonBlank("tier")

            val rateLimit = meJson?.optJSONObject("rate_limit")?.let { rl ->
                val limit = rl.optInt("limit", -1)
                val rem = rl.optInt("remaining", -1)
                if (limit > 0) "$rem / $limit" else null
            }

            var totalRequests: Long? = null
            runCatching {
                val usageResp = api.getUsageStats()
                if (usageResp.isSuccessful) {
                    val uStr = usageResp.body()?.string().orEmpty()
                    val uJson = JSONObject(uStr)
                    totalRequests = uJson.optLongOrNull("total_requests")
                        ?: uJson.optLongOrNull("requests")
                        ?: uJson.optJSONObject("data")?.optLongOrNull("total_requests")
                }
            }

            ProviderUsageInfo(
                isKeyConfigured = true,
                isConnected = true,
                planName = plan,
                creditsRemaining = remaining,
                totalRequests = totalRequests,
                rateLimitDescription = rateLimit,
                statusMessage = "OK"
            )
        } catch (e: Exception) {
            ProviderUsageInfo(
                isKeyConfigured = true,
                isConnected = false,
                statusMessage = e.localizedMessage ?: "Connection failed"
            )
        }
    }

    private fun mapHttpError(code: Int, rawBody: String): ResolveOutcome.Error {
        val message = extractErrorMessage(rawBody).orEmpty()
        val lower = message.lowercase()
        return when {
            code == 401 || code == 403 -> ResolveOutcome.Error.ApiError(
                code,
                message.ifBlank { "Invalid or unauthorized SaveAPI key." }
            )
            code == 429 -> ResolveOutcome.Error.RateLimited
            code == 400 || code == 422 -> {
                if (lower.contains("unsupported") || lower.contains("platform")) {
                    ResolveOutcome.Error.UnsupportedPlatform(message)
                } else {
                    ResolveOutcome.Error.ApiError(code, message.ifBlank { "Invalid URL parameter." })
                }
            }
            code == 404 -> ResolveOutcome.Error.NoMediaFound(message)
            else -> ResolveOutcome.Error.ApiError(code, message.ifBlank { "SaveAPI returned HTTP $code" })
        }
    }

    private fun parseDownloadResponse(originalUrl: String, bodyString: String): ResolveOutcome {
        val root = JSONObject(bodyString)
        if (root.has("success") && !root.optBoolean("success", true)) {
            val errMsg = extractErrorMessage(bodyString) ?: "Unable to resolve media."
            val lower = errMsg.lowercase()
            return when {
                lower.contains("unsupported") -> ResolveOutcome.Error.UnsupportedPlatform(errMsg)
                lower.contains("rate") -> ResolveOutcome.Error.RateLimited
                else -> ResolveOutcome.Error.NoMediaFound(errMsg)
            }
        }

        val payload = root.optJSONObject("data") ?: root
        val platformStr = payload.optStringNonBlank("platform") ?: root.optStringNonBlank("platform")
        val platform = SocialPlatform.fromId(platformStr).let {
            if (it == SocialPlatform.OTHER) SocialPlatform.detectFromUrl(originalUrl) ?: SocialPlatform.OTHER else it
        }

        val title = payload.optStringNonBlank("title")
            ?: payload.optStringNonBlank("caption")
            ?: payload.optStringNonBlank("description")
            ?: "${platform.displayName} Media"

        val author = payload.optStringNonBlank("author")
            ?: payload.optJSONObject("author")?.optStringNonBlank("name")
            ?: payload.optStringNonBlank("username")

        val thumbnail = payload.optStringNonBlank("thumbnail")
            ?: payload.optStringNonBlank("thumb")
            ?: payload.optStringNonBlank("cover")

        val duration = payload.optLongOrNull("duration")
            ?: payload.optDoubleOrNull("duration")?.toLong()

        val rawMedias: JSONArray? = payload.optJSONArray("medias")
            ?: payload.optJSONArray("media")
            ?: payload.optJSONArray("links")
            ?: payload.optJSONArray("formats")
            ?: root.optJSONArray("medias")

        val options = mutableListOf<MediaOption>()
        if (rawMedias != null) {
            for (i in 0 until rawMedias.length()) {
                val item = rawMedias.optJSONObject(i) ?: continue
                val mediaUrl = item.optStringNonBlank("url")
                    ?: item.optStringNonBlank("download_url")
                    ?: item.optStringNonBlank("link")
                    ?: continue

                if (!mediaUrl.startsWith("http", ignoreCase = true)) continue

                val ext = (item.optStringNonBlank("ext")
                    ?: item.optStringNonBlank("extension")
                    ?: guessExtFromUrl(mediaUrl)
                    ?: "mp4").lowercase()

                val typeStr = item.optStringNonBlank("type") ?: payload.optStringNonBlank("type") ?: ext
                val mediaType = MediaType.fromString(typeStr)
                val quality = item.optStringNonBlank("quality")
                    ?: item.optStringNonBlank("label")
                    ?: item.optStringNonBlank("resolution")
                    ?: when {
                        item.has("height") && item.optInt("height") > 0 -> "${item.optInt("height")}p"
                        mediaType == MediaType.AUDIO -> "Audio (${ext.uppercase()})"
                        mediaType == MediaType.IMAGE -> "Original (${ext.uppercase()})"
                        else -> "Standard (${ext.uppercase()})"
                    }

                val size = item.optLongOrNull("size")
                    ?: item.optLongOrNull("filesize")
                    ?: item.optLongOrNull("content_length")

                options.add(
                    MediaOption(
                        id = UUID.randomUUID().toString(),
                        url = mediaUrl,
                        type = mediaType,
                        extension = ext,
                        qualityLabel = quality,
                        fileSizeBytes = size?.takeIf { it > 0L },
                        width = item.optInt("width").takeIf { it > 0 },
                        height = item.optInt("height").takeIf { it > 0 },
                        hasAudio = item.optBoolean("audio", true)
                    )
                )
            }
        } else {
            val singleUrl = payload.optStringNonBlank("url") ?: payload.optStringNonBlank("download_url")
            if (!singleUrl.isNullOrBlank() && singleUrl.startsWith("http", ignoreCase = true)) {
                val mediaType = MediaType.fromString(payload.optStringNonBlank("type"))
                val ext = guessExtFromUrl(singleUrl) ?: when (mediaType) {
                    MediaType.AUDIO -> "mp3"
                    MediaType.IMAGE -> "jpg"
                    MediaType.VIDEO -> "mp4"
                }
                options.add(
                    MediaOption(
                        id = UUID.randomUUID().toString(),
                        url = singleUrl,
                        type = mediaType,
                        extension = ext,
                        qualityLabel = "Best (${ext.uppercase()})"
                    )
                )
            }
        }

        if (options.isEmpty()) {
            return ResolveOutcome.Error.NoMediaFound()
        }

        val creditsObj = root.optJSONObject("credits") ?: payload.optJSONObject("credits")
        val creditsSpent = creditsObj?.optDoubleOrNull("spent")
        val creditsRemaining = creditsObj?.optDoubleOrNull("remaining")

        return ResolveOutcome.Success(
            MediaResolveResult(
                originalUrl = originalUrl,
                platform = platform,
                title = title.take(140),
                author = author,
                thumbnailUrl = thumbnail,
                durationSeconds = duration,
                options = options,
                creditsSpent = creditsSpent,
                creditsRemaining = creditsRemaining
            )
        )
    }

    private fun extractErrorMessage(raw: String): String? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            obj.optStringNonBlank("message")
                ?: obj.optStringNonBlank("error")
                ?: obj.optJSONObject("error")?.optStringNonBlank("message")
                ?: obj.optStringNonBlank("detail")
        }.getOrNull()
    }

    private fun guessExtFromUrl(url: String): String? {
        val path = url.substringBefore("?").substringAfterLast("/", "")
        val ext = path.substringAfterLast(".", "").lowercase()
        return if (ext in listOf("mp4", "webm", "mov", "mkv", "jpg", "jpeg", "png", "webp", "mp3", "m4a", "aac", "ogg")) ext else null
    }

    private fun JSONObject.optStringNonBlank(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "").trim()
        return if (v.isBlank() || v.equals("null", ignoreCase = true)) null else v
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val d = optDouble(key, Double.NaN)
        return if (d.isNaN()) null else d
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        val l = optLong(key, -1L)
        return if (l < 0L) null else l
    }
}
