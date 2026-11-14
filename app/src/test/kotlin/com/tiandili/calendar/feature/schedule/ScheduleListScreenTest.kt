package com.tiandili.calendar.feature.schedule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.domain.schedule.Schedule
import com.tiandili.calendar.domain.schedule.ScheduleTiming
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `empty state shows add action and dispatches its click`() {
        var addClicked = false
        composeRule.setContent {
            TiandiliTheme {
                ScheduleListScreen(
                    state = ScheduleListUiState(),
                    onAddSchedule = { addClicked = true },
                    onEditSchedule = {},
                    onDeleteSchedule = {},
                )
            }
        }

        composeRule.onNodeWithText("暂无日程").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("添加日程").performClick()
        composeRule.runOnIdle {
            assertThat(addClicked).isTrue()
        }
    }

    @Test
    fun `schedule row shows timing and dispatches edit and delete actions`() {
        val schedule = Schedule(
            id = 42,
            title = "晨会",
            timing = ScheduleTiming.Timed(
                startsAt = LocalDateTime.of(2025, 2, 2, 9, 0),
                endsAt = LocalDateTime.of(2025, 2, 2, 10, 0),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
        )
        var editedId: Long? = null
        var deletedSchedule: Schedule? = null
        composeRule.setContent {
            TiandiliTheme {
                ScheduleListScreen(
                    state = ScheduleListUiState(schedules = listOf(schedule)),
                    onAddSchedule = {},
                    onEditSchedule = { editedId = it },
                    onDeleteSchedule = { deletedSchedule = it },
                )
            }
        }

        composeRule.onNodeWithText("晨会").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("2月2日 · 09:00–10:00").assertIsDisplayed()
        composeRule.runOnIdle {
            assertThat(editedId).isEqualTo(42)
        }

        composeRule.onNodeWithContentDescription("删除日程：晨会").performClick()
        composeRule.runOnIdle {
            assertThat(deletedSchedule).isEqualTo(schedule)
        }
    }
}
