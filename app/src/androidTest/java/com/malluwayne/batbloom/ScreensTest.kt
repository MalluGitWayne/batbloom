package com.malluwayne.batbloom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Opens every tab and the main sheets in the real app, so a crash or a missing screen is caught before release.
 * Runs on a device or emulator: ./gradlew connectedDebugAndroidTest
 */
class ScreensTest {
    @get:Rule(order = 0) val notifications: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Before fun setUp() {
        val p = BatbloomApp.instance.prefs
        rule.runOnUiThread {
            p.setSmokingSetupDone()
            p.setExamTab(true); p.setQuitTab(true); p.setMedsTab(true)
            p.markBadgesSeen(com.malluwayne.batbloom.quit.Badges.all.map { it.id })
            Nav.tab.value = 6
        }
        rule.waitForIdle()
    }

    private fun tab(label: String) {
        rule.onAllNodesWithText(label).onFirst().performClick()
        rule.waitForIdle()
    }

    @Test fun homeShowsCards() {
        rule.onNodeWithText("FOCUS TODAY").assertIsDisplayed()
        rule.onNodeWithText("Start focus").assertIsDisplayed()
    }

    @Test fun everyTabOpens() {
        tab("Focus"); rule.onNodeWithText("Start").assertIsDisplayed()
        tab("Exam"); rule.onNodeWithText("FMGE preparation").assertIsDisplayed()
        tab("Quit"); rule.onAllNodesWithText("Quit").onFirst().assertIsDisplayed()
        tab("Meds"); rule.onNodeWithText("Medications").assertIsDisplayed()
        tab("Settings"); rule.onNodeWithText("Tabs").assertIsDisplayed()
    }

    @Test fun gardenAndAwardsOpenFromHome() {
        rule.onNodeWithText("Garden").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Awards").performClick(); rule.waitForIdle()
        rule.onNodeWithText("SCORE").assertIsDisplayed()
    }

    @Test fun examQuestionCounterAdds() {
        tab("Exam")
        val before = com.malluwayne.batbloom.stats.ExamPlan.parseMcq(BatbloomApp.instance.prefs.mcq.value)[java.time.LocalDate.now()] ?: 0
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("+10"))
        rule.onNodeWithText("+10").performClick(); rule.waitForIdle()
        val after = com.malluwayne.batbloom.stats.ExamPlan.parseMcq(BatbloomApp.instance.prefs.mcq.value)[java.time.LocalDate.now()] ?: 0
        assert(after == before + 10) { "expected ${before + 10}, got $after" }
        rule.runOnUiThread {
            val p = BatbloomApp.instance.prefs
            val m = com.malluwayne.batbloom.stats.ExamPlan.parseMcq(p.mcq.value).toMutableMap()
            m[java.time.LocalDate.now()] = before
            p.setMcq(com.malluwayne.batbloom.stats.ExamPlan.formatMcq(m, java.time.LocalDate.now())); p.addMcqTotal(-10)
        }
    }

    @Test fun addMedicationFlowReachesReview() {
        tab("Meds")
        rule.onNodeWithText("+ Add").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Medication name").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Medications").assertIsDisplayed()
    }

    /** The confirm-before-log sheet opens fully and closes without logging anything. */
    @Test fun medLogSheetOpensAndCancels() {
        tab("Meds")
        // Today always has varenicline doses during the course; skip when outside it.
        if (rule.onAllNodesWithText("Log").fetchSemanticsNodes().isEmpty()) return
        rule.onAllNodesWithText("Log").onFirst().performClick(); rule.waitForIdle()
        rule.onNodeWithText("Skip all").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Medications").assertIsDisplayed()
    }

    /** Dragging-free check that the duration slider is on screen with its value. */
    @Test fun focusSliderShows() {
        tab("Focus")
        rule.onNodeWithText("${BatbloomApp.instance.prefs.minutes.value} min").assertIsDisplayed()
    }

    @Test fun cravingBreathingOpensAndCloses() {
        tab("Quit")
        val quit = BatbloomApp.instance.prefs.quitDate.value
        if (quit == null) return
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Craving? Breathe"))
        rule.onNodeWithText("Craving? Breathe").performClick(); rule.waitForIdle()
        rule.onNodeWithText("The craving has passed").assertIsDisplayed()
    }
}
