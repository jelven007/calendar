package com.tiandili.calendar.feature.settings

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

const val RELEASE_PAGE = "https://github.com/jelven007/calendar/tree/main/releases"
private const val METADATA_URL =
    "https://raw.githubusercontent.com/jelven007/calendar/main/releases/latest.json"

enum class UpdateStatus { IDLE, CHECKING, CURRENT, AVAILABLE, INCOMPATIBLE, FAILED }
data class ReleaseInfo(val versionName: String, val versionCode: Int, val minSdk: Int, val apkUrl: String)
data class ReleaseUpdate(val status: UpdateStatus = UpdateStatus.IDLE, val release: ReleaseInfo? = null)

@JsonClass(generateAdapter = true)
data class ReleaseMetadata(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val apk: String,
    val sha256: String,
    val apkUrl: String? = null,
)

class ReleaseUpdateParser(moshi: Moshi) {
    private val adapter = moshi.adapter(ReleaseMetadata::class.java)
    fun parse(json: String, currentCode: Int, deviceSdk: Int): ReleaseUpdate {
        require(json.toByteArray().size <= 65_536) { "更新信息过大" }
        val metadata = try { adapter.fromJson(json) } catch (_: Exception) { null }
        requireNotNull(metadata) { "更新信息格式不正确" }
        require(metadata.applicationId == "com.tiandili.calendar") { "应用标识不匹配" }
        require(metadata.versionCode > 0 && metadata.minSdk in 26..100 &&
            metadata.versionName.matches(Regex("\\d+\\.\\d+\\.\\d+"))) { "版本信息不正确" }
        require(metadata.apk.matches(Regex("[A-Za-z0-9._-]+\\.apk")) &&
            metadata.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "下载信息不正确" }
        val trusted = "https://raw.githubusercontent.com/jelven007/calendar/main/releases/" +
            "${metadata.versionName}/${metadata.apk}"
        val download = metadata.apkUrl ?: trusted
        // Only the exact repository artifact is accepted, including no user info, query or fragment.
        require(download == trusted && download.toHttpUrl().isHttps) { "下载来源不可信" }
        val release = ReleaseInfo(metadata.versionName, metadata.versionCode, metadata.minSdk, download)
        return ReleaseUpdate(when {
            metadata.versionCode <= currentCode -> UpdateStatus.CURRENT
            deviceSdk < metadata.minSdk -> UpdateStatus.INCOMPATIBLE
            else -> UpdateStatus.AVAILABLE
        }, release)
    }
}

fun interface ReleaseMetadataSource { suspend fun fetch(): String }

class HttpReleaseMetadataSource : ReleaseMetadataSource {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    override suspend fun fetch(): String =
        client.newCall(Request.Builder().url(METADATA_URL).build()).execute().use { response ->
            check(response.isSuccessful) { "更新服务暂不可用" }
            val body = requireNotNull(response.body)
            require(body.contentLength() <= 65_536) { "更新信息过大" }
            body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 65_536) { "更新信息过大" }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        }
}

class ReleaseUpdateClient(
    private val source: ReleaseMetadataSource = HttpReleaseMetadataSource(),
    private val parser: ReleaseUpdateParser = ReleaseUpdateParser(Moshi.Builder().build()),
) {
    suspend fun check(currentCode: Int, deviceSdk: Int): ReleaseUpdate = try {
        parser.parse(source.fetch(), currentCode, deviceSdk)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        ReleaseUpdate(UpdateStatus.FAILED)
    }
}
