package com.tiandili.calendar.feature.schedule

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.tiandili.calendar.domain.schedule.ScheduleValidationError
import com.tiandili.calendar.ui.theme.TiandiliTheme
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleEditorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `editor shows field error and dispatches title and save actions`() {
        var changedTitle: String? = null
        var saveClicked = false
        composeRule.setContent {
            TiandiliTheme {
                ScheduleEditorScreen(
                    state = ScheduleEditorUiState(
                        startDate = LocalDate.of(2025, 2, 2),
                        endDate = LocalDate.of(2025, 2, 2),
                        errors = listOf(ScheduleValidationError.TITLE_REQUIRED),
                    ),
                    onBack = {},
                    onTitleChanged = { changedTitle = it },
                    onAllDayChanged = {},
                    onStartDateChanged = {},
                    onEndDateChanged = {},
                    onStartTimeChanged = {},
                    onEndTimeChanged = {},
                    onRecurrenceChanged = {},
                    onReminderMinutesChanged = {},
                    onNoteChanged = {},
                    onSave = { saveClicked = true },
                )
            }
        }

        composeRule.onNodeWithText("标题").performTextInput("看牙")
        composeRule.onNodeWithText("请输入日程标题").assertIsDisplayed()
        composeRule.onNodeWithText("全天").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("保存日程").performClick()
        composeRule.onNodeWithText("重复").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("提醒").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("备注").performScrollTo().assertIsDisplayed()

        composeRule.runOnIdle {
            assertThat(changedTitle).isEqualTo("看牙")
            assertThat(saveClicked).isTrue()
        }
    }
}
