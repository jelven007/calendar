package com.tiandili.calendar

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OpenSourceLicensesTest {
    @Test
    fun `bundled notices preserve upstream MIT copyrights and transitive license texts`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val text = context.assets.open("open_source_licenses.txt")
            .bufferedReader().use { it.readText() }

        assertThat(text).contains("Copyright (c) 2018 6tail")
        assertThat(text).contains("Copyright (c) 2019 Kizito Nwose")
        assertThat(text).contains("Copyright 2008 Google Inc.")
        assertThat(text).contains("Mozilla Public License Version 2.0")
        assertThat(text).contains("publicsuffixes.gz is compiled from The Public Suffix List")
    }

    @Test
    fun `bundled notices contain the complete Apache license and widget dependencies`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val text = context.assets.open("open_source_licenses.txt")
            .bufferedReader().use { it.readText() }

        assertThat(text).contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION")
        assertThat(text).contains("9. Accepting Warranty or Additional Liability.")
        assertThat(text).contains("END OF TERMS AND CONDITIONS")
        assertThat(text).contains("androidx.glance:glance-appwidget:1.1.1")
        assertThat(text).contains("androidx.work:work-runtime:2.10.1")
        assertThat(text).contains("CC BY 4.0")
    }
}
