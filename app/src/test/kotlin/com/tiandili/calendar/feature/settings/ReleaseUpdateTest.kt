package com.tiandili.calendar.feature.settings

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseUpdateTest {
    private fun json(code: Int = 3, minSdk: Int = 26, extras: String = "") = """
        {"applicationId":"com.tiandili.calendar","versionName":"0.3.0",
        "versionCode":$code,"minSdk":$minSdk,"apk":"tiandili-0.3.0-release.apk",
        "sha256":"${"a".repeat(64)}"$extras}
    """.trimIndent()

    @Test fun `old published metadata derives a trusted download and compares version code`() {
        val result = ReleaseUpdateParser(Moshi.Builder().build()).parse(json(), 2, 35)
        assertThat(result.status).isEqualTo(UpdateStatus.AVAILABLE)
        assertThat(result.release?.apkUrl).isEqualTo(
            "https://raw.githubusercontent.com/jelven007/calendar/main/releases/0.3.0/tiandili-0.3.0-release.apk")
        assertThat(ReleaseUpdateParser(Moshi.Builder().build()).parse(json(1), 2, 35).status)
            .isEqualTo(UpdateStatus.CURRENT)
    }

    @Test fun `newer incompatible release does not offer an install download`() {
        val result = ReleaseUpdateParser(Moshi.Builder().build()).parse(json(minSdk = 36), 2, 26)
        assertThat(result.status).isEqualTo(UpdateStatus.INCOMPATIBLE)
    }

    @Test fun `foreign application unsafe URL and malformed version are rejected`() {
        val parser = ReleaseUpdateParser(Moshi.Builder().build())
        listOf(
            json().replace("com.tiandili.calendar", "com.other.app"),
            json(extras = ""","apkUrl":"http://example.com/app.apk""""),
            json(extras = ""","apkUrl":"https://example.com/app.apk""""),
            json().replace("0.3.0", "../private"),
            json().replace("tiandili-0.3.0-release.apk", "../app.apk"),
            json(code = 0),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { parser.parse(invalid, 2, 35) }
        }
    }

    @Test fun `failed network check can be retried`() = runTest {
        var calls = 0
        val client = ReleaseUpdateClient(ReleaseMetadataSource {
            calls++
            if (calls == 1) throw java.io.IOException("offline")
            json()
        }, ReleaseUpdateParser(Moshi.Builder().build()))
        assertThat(client.check(2, 35).status).isEqualTo(UpdateStatus.FAILED)
        assertThat(client.check(2, 35).status).isEqualTo(UpdateStatus.AVAILABLE)
    }
}
