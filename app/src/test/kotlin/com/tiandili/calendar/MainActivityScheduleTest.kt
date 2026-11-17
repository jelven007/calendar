package com.tiandili.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityScheduleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `activity wires the real schedule feature`() {
        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("日程").performClick()

        composeRule.onNodeWithContentDescription("添加日程").assertIsDisplayed()
    }

    @Test
    fun `editor uses the full screen and restores tabs on close`() {
        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("日程").performClick()
        composeRule.onNodeWithContentDescription("添加日程").performClick()
        composeRule.onNodeWithText("我的").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("保存日程").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回日程列表").performClick()
        composeRule.onNodeWithText("我的").assertIsDisplayed()
    }
}
