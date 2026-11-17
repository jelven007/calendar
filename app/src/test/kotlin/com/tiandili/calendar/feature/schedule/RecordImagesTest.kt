package com.tiandili.calendar.feature.schedule

import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordImagesTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `selected content image loads a thumbnail and opens the image browser`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = "content://test/images/1".toUri()
        val output = ByteArrayOutputStream()
        Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output)
        val bytes = output.toByteArray()
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        composeRule.setContent { TiandiliTheme { RecordImages(listOf(uri.toString())) } }
        composeRule.waitUntil(5000) { composeRule.onNodeWithContentDescription("图片 1").isDisplayed() }
        composeRule.onNodeWithContentDescription("图片 1").performClick()
        composeRule.onNodeWithText("图片 1 / 1").assertIsDisplayed()
    }

    @Test fun `lost image reference offers reselect without deleting the source`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = "content://test/images/missing".toUri()
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { throw FileNotFoundException() }
        var selected: Int? = null
        composeRule.setContent { TiandiliTheme {
            RecordImages(listOf(uri.toString()), onReselect = { selected = it })
        } }
        composeRule.waitUntil(5000) { composeRule.onNodeWithText("重新选择").isDisplayed() }
        composeRule.onNodeWithText("重新选择").performClick()
        composeRule.runOnIdle {
            assertThat(selected).isEqualTo(0)
            assertThat(shadowOf(context.contentResolver).deleteStatements).isEmpty()
        }
    }
}
