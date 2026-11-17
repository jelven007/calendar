package com.tiandili.calendar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.domain.schedule.*
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class AppStartupCompatibilityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun `application opens database and category editor on supported APIs`() = runBlocking<Unit> {
        rule.onNodeWithText("日历").assertIsDisplayed()
        val app = rule.activity.application as TiandiliApplication
        val today = LocalDate.now()
        val record = Schedule(title = "兼容性启动检查", timing = ScheduleTiming.AllDay(today, today.plusDays(1)),
            kind = ScheduleKind.TODO, checklist = listOf(ChecklistItem("数据库往返", true), ChecklistItem("原生编辑")))
        val id = app.scheduleRepository.save(record)
        val stored = app.scheduleRepository.get(id)!!
        assertThat(stored.kind).isEqualTo(ScheduleKind.TODO)
        assertThat(stored.checklist).isEqualTo(record.checklist)
        assertThat(app.database.openHelper.readableDatabase.version).isEqualTo(4)
        rule.onNodeWithText("我的").performClick()
        rule.onNodeWithTag("mine_shortcut_TODO").performClick()
        rule.onNodeWithContentDescription("添加待办").performClick()
        rule.onNodeWithContentDescription("保存待办").assertIsDisplayed()
        rule.onNodeWithText("我的").assertDoesNotExist()
    }
}
