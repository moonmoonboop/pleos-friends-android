package com.pleosfriends

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalStopTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun tap(tag:String)=compose.onNodeWithTag(tag).performClick()
    private fun voice(value:String)=compose.waitUntil(7000) { compose.onAllNodes(hasText(value) and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    @Test fun personalStopVoiceAndPicksCancel() {
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        fun state():TravelState { var s:TravelState?=null;scenario.onActivity { s=it.snapshotForRecording() };return s!! }
        try {
            scenario.onActivity { it.simulateForRecording("showcase") }
            compose.waitUntil(12000) { runCatching { compose.onAllNodesWithTag("group-map").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            val before=state();tap("calendar")
            compose.onNodeWithText("전체 여행 일정").assertExists()
            tap("close-panel");tap("waypoint-add")
            tap("realtime-gleo");voice("가평제알휴게소");tap("input-save");tap("insert-first")
            tap("scope-personal")
            assertEquals("가평제알휴게소",JourneyEngine.visibleStops(state(),"me").first().name)
            tap("notify-voice")
            voice("sorry I have to go to the bathroom");tap("input-save")
            compose.waitUntil(7000) { compose.onAllNodesWithTag("completion-confirm").fetchSemanticsNodes().isNotEmpty() }
            tap("completion-confirm")
            assertEquals("가평제알휴게소",JourneyEngine.visibleStops(state(),"me").first().name)
            assertEquals(JourneyEngine.visibleStops(before,"a"),JourneyEngine.visibleStops(state(),"a"))
            assertEquals(JourneyEngine.arrivalClock(before,"a"),JourneyEngine.arrivalClock(state(),"a"))
            assertNotEquals(JourneyEngine.arrivalClock(before),JourneyEngine.arrivalClock(state()))
            assertEquals("sorry I have to go to the bathroom",state().notes.last().voice)
            compose.onNodeWithText("플픽").performClick()
            compose.onNodeWithTag("filter-카페").performScrollTo().performClick()
            compose.onNodeWithTag("basis-도착지").performScrollTo().performClick()
            assertEquals("도착지",state().picks.basis)
            compose.onNodeWithTag("picks-list").performScrollToNode(hasTestTag("pick-add-roasters"))
            tap("pick-add-roasters");compose.onNodeWithTag("realtime-candidate").assertExists()
            tap("close-panel");assertEquals(1,state().changes.size)
        } finally { scenario.close() }
    }
    @Test fun cancelledVoiceKeepsDraftAndNoNotificationCommitsOnlyMyRoute() {
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        fun state():TravelState {var value:TravelState?=null;scenario.onActivity {value=it.snapshotForRecording()};return value!!}
        try {
            scenario.onActivity {it.simulateForRecording("showcase")}
            compose.waitUntil(12000) {runCatching {compose.onAllNodesWithTag("waypoint-add").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
            val before=state()
            tap("waypoint-add");tap("realtime-gleo");voice("가평제알휴게소");tap("input-save");tap("insert-first")
            compose.onNodeWithTag("notify-no").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithTag("notify-voice").assertIsDisplayed().assertIsNotEnabled()
            tap("scope-personal")
            assertEquals(1,state().changes.size)
            assertEquals("가평제알휴게소",JourneyEngine.visibleStops(state(),"me").first().name)
            tap("notify-voice");voice("sorry I have to go to the bathroom")
            compose.onNodeWithText("보내지 않기").performClick()
            assertEquals(1,state().changes.size)
            assertTrue(state().notes.isEmpty())
            compose.onNodeWithTag("scope-personal").assertIsDisplayed()
            compose.onNodeWithTag("notify-no").assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("completion-confirm").performClick()
            assertEquals(1,state().changes.size)
            assertEquals(before.notes,state().notes)
            assertEquals("가평제알휴게소",JourneyEngine.visibleStops(state(),"me").first().name)
            assertEquals(JourneyEngine.visibleStops(before,"a"),JourneyEngine.visibleStops(state(),"a"))
        } finally {scenario.close()}
    }

}
