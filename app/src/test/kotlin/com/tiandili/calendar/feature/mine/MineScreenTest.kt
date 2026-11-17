package com.tiandili.calendar.feature.mine

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.data.settings.UserPreferences
import com.tiandili.calendar.domain.schedule.*
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.time.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MineScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 18)
    private val clock = Clock.fixed(Instant.parse("2026-11-18T01:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Test fun `ten shortcuts six summaries and search dispatch actual record actions`() {
        var opened: ScheduleKind? = null
        var created: ScheduleKind? = null
        var recordId: Long? = null
        var settings = 0
        var tools = 0
        compose.setContent { TiandiliTheme { MineScreen(
            listOf(Schedule(42, "旅行备忘", ScheduleTiming.AllDay(today, today.plusDays(1)),
                note = "预订酒店", kind = ScheduleKind.NOTE)), UserPreferences(),
            { opened = it }, { settings++ }, { tools++ }, clock = clock,
            onCreateKind = { created = it }, onOpenRecord = { recordId = it }) } }
        ScheduleKind.entries.forEach { kind ->
            compose.onNodeWithTag("mine_shortcut_${kind.name}").performScrollTo().performClick()
            compose.runOnIdle { assertThat(opened).isEqualTo(kind) }
        }
        compose.onNodeWithTag("mine_shortcut_8").performClick()
        compose.onNodeWithTag("mine_shortcut_9").performClick()
        compose.runOnIdle { assertThat(settings).isEqualTo(1); assertThat(tools).isEqualTo(1) }
        listOf(ScheduleKind.BIRTHDAY, ScheduleKind.ANNIVERSARY, ScheduleKind.NOTE,
            ScheduleKind.SCHEDULE, ScheduleKind.ALARM, ScheduleKind.TODO).forEach {
            compose.onNodeWithTag("mine_content").performScrollToNode(hasTestTag("mine_summary_${it.name}"))
            compose.onNodeWithTag("mine_summary_${it.name}").assertIsDisplayed()
            compose.onNodeWithContentDescription("新增${it.label}").performClick()
            compose.runOnIdle { assertThat(created).isEqualTo(it) }
        }
        compose.onNodeWithTag("mine_content").performScrollToNode(hasTestTag("mine_search"))
        compose.onNodeWithTag("mine_search").performTextInput("酒店")
        compose.onNodeWithText("旅行备忘").performScrollTo().performClick()
        compose.runOnIdle { assertThat(recordId).isEqualTo(42) }
    }

    @Test fun `summary alarm switch and todo completion operate on saved record ids`() {
        var enabled: Pair<Long, Boolean>? = null
        var completed: Pair<Long, Boolean>? = null
        val records = listOf(
            Schedule(1, "早起", ScheduleTiming.AllDay(today, today.plusDays(1)), kind = ScheduleKind.ALARM),
            Schedule(2, "读书", ScheduleTiming.AllDay(today, today.plusDays(1)), kind = ScheduleKind.TODO,
                checklist = listOf(ChecklistItem("第一章", true), ChecklistItem("第二章"))))
        compose.setContent { TiandiliTheme { MineScreen(records, UserPreferences(), {}, {}, {},
            clock = clock, onSetEnabled = { id, value -> enabled = id to value },
            onSetCompleted = { id, value -> completed = id to value }) } }
        compose.onNodeWithTag("mine_content").performScrollToNode(hasTestTag("mine_alarm_1"))
        compose.onNodeWithTag("mine_alarm_1").performClick()
        compose.onNodeWithTag("mine_content").performScrollToNode(hasTestTag("mine_todo_2"))
        compose.onNodeWithTag("mine_todo_2").performClick()
        compose.onNodeWithText("1/2 项完成").assertIsDisplayed()
        compose.runOnIdle { assertThat(enabled).isEqualTo(1L to false)
            assertThat(completed).isEqualTo(2L to true) }
    }
}
