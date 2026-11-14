package com.tiandili.calendar.platform.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.tiandili.calendar.domain.widget.*
import com.tiandili.calendar.domain.weather.CacheFreshness
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
class WidgetRenderingTest {
    private val now = Instant.parse("2025-02-02T01:00:00Z")
    private val zone = ZoneId.of("Asia/Shanghai")
    private val snapshot = WidgetSnapshot(now.toEpochMilli(), "2025-02-02", "星期日",
        "正月初五", null, "出行 · 祭祀", "动土 · 安葬",
        weather = WidgetWeatherSummary("北京", "晴", "6°", "2° / 9°",
            now.minusSeconds(3600).toEpochMilli(), CacheFreshness.STALE))

    @Test fun `native compact remote views fit normal and enlarged font`() = runBlocking<Unit> {
        render(wide = false, fontScale = 1f)
        render(wide = false, fontScale = 1.3f)
    }

    @Test fun `native wide remote views fit normal and enlarged font`() = runBlocking<Unit> {
        render(wide = true, fontScale = 1f)
        render(wide = true, fontScale = 1.3f)
    }

    private suspend fun render(wide: Boolean, fontScale: Float) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = android.content.res.Configuration(base.resources.configuration).apply {
            this.fontScale = fontScale
        }
        val context = base.createConfigurationContext(configuration)
        val width = if (wide) 280 else 140
        val height = 160
        val result = GlanceRemoteViews().compose(context, DpSize(width.dp, height.dp)) {
            CalendarWidgetContent(snapshot, wide, false, now, zone)
        }
        val view = result.remoteViews.apply(context, FrameLayout(context))
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val directory = File("build/outputs/widget-previews").apply { mkdirs() }
        File(directory, "${if (wide) "wide" else "compact"}-$fontScale.png")
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val texts = mutableListOf<TextView>()
        collectTexts(view, texts)
        assertThat(texts.map { it.text.toString() }).contains("Open-Meteo")
        texts.filter { it.text.isNotEmpty() }.forEach { text ->
            assertWithMessage("${text.text} text layout at scale $fontScale")
                .that(text.height - text.paddingTop - text.paddingBottom)
                .isAtLeast(text.layout.height)
            var top = 0
            var left = 0
            var current: View? = text
            while (current != null && current !== view) {
                val parent = current.parent as? ViewGroup
                if (parent != null && parent.clipChildren) {
                    assertWithMessage("${text.text} ancestor clipping at scale $fontScale")
                        .that(current.bottom).isAtMost(parent.height)
                }
                top += current.top
                left += current.left
                current = current.parent as? View
            }
            assertWithMessage("${text.text} bottom at scale $fontScale").that(top + text.height)
                .isAtMost(height)
            assertWithMessage("${text.text} right at scale $fontScale").that(left + text.width)
                .isAtMost(width)
            assertWithMessage("${text.text} visible height").that(text.height).isGreaterThan(0)
        }
    }

    private fun collectTexts(view: View, output: MutableList<TextView>) {
        if (view is TextView) output += view
        if (view is ViewGroup) for (index in 0 until view.childCount) collectTexts(view.getChildAt(index), output)
    }
}
