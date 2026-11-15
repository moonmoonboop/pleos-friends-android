package com.pleosfriends

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.graphics.Bitmap
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The five-scene recording script. All names, addresses, phone inputs and dialogue come from JSON. */
@RunWith(AndroidJUnit4::class)
class BusanStoryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val fixture by lazy { Gson().fromJson(context.assets.open("busan-roadtrip.json").bufferedReader().use { it.readText() },StoryFixture::class.java) }
    private val recording get() = InstrumentationRegistry.getArguments().getString("record") == "true"
    private fun pause(name: String, audio: Boolean = false) {
        compose.waitForIdle()
        Thread.sleep(200) // Allow native compositor to present the settled Compose frame.
        instrumentation.sendStatus(2,Bundle().apply { putString("stream","SCENE: $name\n") })
        if (recording) Thread.sleep(if (audio) 6000 else fixture.sceneHoldMs)
        if (recording && name.take(2) !in setOf("01","02","03","04","05","07","08","09","15","17","22","25")) return
        val dir=File(context.filesDir,"screenshots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(dir,"${name.replace(Regex("[^a-zA-Z0-9_-]"),"-")}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    private fun tap(tag: String) = compose.onNodeWithTag(tag).performClick()
    private fun type(tag: String,value: String) {
        compose.onNodeWithTag(tag).performTextClearance();compose.onNodeWithTag(tag).performTextInput(value)
        if(tag=="stop-input") {
            compose.onNodeWithTag(tag).performImeAction()
            instrumentation.waitForIdleSync();Thread.sleep(650);compose.waitForIdle()
        }
    }
    private fun waitTag(tag: String) = compose.waitUntil(12000) { runCatching { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    private fun waitVoice(text: String) = compose.waitUntil(7000) { compose.onAllNodes(hasText(text) and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    private fun input(tag: String,value: String) { tap(tag); tap("gleo");waitVoice(value);if(recording) Thread.sleep(800);tap("input-save") }
    private fun scrollTap(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun dragVisible(sourceTag:String,targetTag:String) {
        val node=compose.onNodeWithTag(sourceTag)
        val source=node.fetchSemanticsNode()
        val target=compose.onNodeWithTag(targetTag).fetchSemanticsNode()
        val from=source.boundsInRoot
        val to=target.boundsInRoot
        val end=Offset(to.center.x-from.left,to.center.y-from.top)
        if(!recording) { node.performTouchInput { swipe(center,end,950) };return }
        var origin=Offset.Zero
        instrumentation.runOnMainSync {
            val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().first()
            val screen=IntArray(2);val window=IntArray(2)
            activity.window.decorView.getLocationOnScreen(screen);activity.window.decorView.getLocationInWindow(window)
            origin=Offset((screen[0]-window[0]).toFloat(),(screen[1]-window[1]).toFloat())
        }
        // Window bounds include any Compose-owner offset left by native keyboard resize.
        val start=source.boundsInWindow.center+origin
        val finish=target.boundsInWindow.center+origin
        val downTime=SystemClock.uptimeMillis()
        fun inject(action:Int,point:Offset) {
            val event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,point.x,point.y,0)
            event.source=InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN,start)
        // Real native touch frames keep the visible gesture smooth without test-idle gaps.
        for(frame in 1..28) {
            val progress=frame/28f
            Thread.sleep(30)
            inject(MotionEvent.ACTION_MOVE,start+(finish-start)*progress)
            compose.mainClock.advanceTimeBy(32)
        }
        inject(MotionEvent.ACTION_UP,finish)
        Thread.sleep(150)
        compose.waitForIdle()
    }
    private fun assertPlanned(index:Int,name:String) {
        compose.onNodeWithTag("planned-stop-$index").assert(hasAnyDescendant(hasText(name)))
    }
    private fun dragFirst() {
        dragVisible("realtime-candidate","realtime-drop-0")
    }
    private fun planInitialStops(snapshot:()->TravelState) {
        tap("edit-day-0")
        compose.onAllNodesWithTag("stop-address").assertCountEquals(0)
        type("stop-input",fixture.lunch.name)
        pause("02a-enter-a-stop");dragVisible("candidate-stop","drop-zone-0")
        assertPlanned(0,fixture.lunch.name)
        pause("02b-lunch-only")
        assertTrue(snapshot().days[0].stops.isEmpty())
        tap("save-stops")
        assertEquals(listOf(fixture.lunch.name),snapshot().days[0].stops.map { it.name })
        assertTrue(snapshot().days[0].stops.none { it.name.contains("카페") })
        pause("02d-saved-lunch-and-hotel")
        tap("edit-day-0");assertPlanned(0,fixture.lunch.name)
        compose.onAllNodesWithText("카페 미정",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText("카페 · 미정").assertCountEquals(0)
        tap("save-stops")
        assertEquals(listOf(fixture.lunch.name),snapshot().days[0].stops.map { it.name })
    }
    @Test fun planningDragReview() {
        val scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra("scenario","busan"))
        fun snapshot():TravelState { var value:TravelState?=null;scenario.onActivity { value=it.snapshotForRecording() };return value!! }
        try {
            scenario.onActivity { it.presentDesignReview(TripRules.resize(JourneyEngine.seed(fixture).copy(screen=Screen.SETUP,destination=fixture.destination.name),fixture.nights)) }
            waitTag("edit-day-0")
            planInitialStops { snapshot() }
            pause("02e-final-day-plan")
        } finally { scenario.close() }
    }
    @Test fun busanFiveSceneRecording() {
        val intent=Intent(context,MainActivity::class.java).putExtra("scenario","busan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val scenario=ActivityScenario.launch<MainActivity>(intent)
        fun snapshot():TravelState { var s:TravelState?=null;scenario.onActivity { s=it.snapshotForRecording() };return s!! }
        fun simulate(action:String,id:String="") { scenario.onActivity { it.simulateForRecording(action,id) };compose.waitForIdle() }
        try {
            waitTag("home-trip");pause("01-home")
            compose.onNodeWithTag("viewer-me").assertExists()
            compose.onNodeWithTag("connect-dock").assertExists()
            compose.onAllNodesWithText("장면",substring=true).assertCountEquals(0)
            tap("home-trip");waitTag("destination")
            input("destination",fixture.destination.name)
            assertEquals("부산 호텔",snapshot().destination)
            tap("duration");compose.onAllNodesWithText("${fixture.nights}박",useUnmergedTree=true).onLast().performClick();tap("duration-save")
            planInitialStops { snapshot() }
            pause("02-three-day-plan");tap("setup-next");input("group-name",fixture.groupName)
            assertEquals("대학 동기 부산 원정대",snapshot().groupName)
            fixture.phones.forEachIndexed { i,phone ->
                compose.onAllNodesWithContentDescription("친구 초대").onFirst().performClick()
                type("invite-phone",phone);tap("send-invite")
                compose.waitUntil(7000) { snapshot().joined >= i+1 }
            }
            compose.onNodeWithText("도현").assertExists();compose.onNodeWithText("서윤").assertExists()
            pause("03-three-vehicles-six-friends");tap("friends-next");tap("confirm-next");pause("03b-ready-to-leave");tap("start-trip");waitTag("group-map")
            assertEquals(3,snapshot().members.size)
            assertEquals(listOf("예빈","도현","서윤"),snapshot().members.map { it.name })
            pause("04-connected-roads")

            tap("calendar");pause("05-full-itinerary");tap("close-panel");pause("05b-back-to-main-map");tap("waypoint-add")
            tap("realtime-gleo");waitVoice(fixture.rest.name);pause("06-rest-voice");tap("input-save")
            dragFirst();tap("scope-group");pause("07-group-route-choice")
            snapshot().members.forEach { assertEquals(fixture.rest.name,JourneyEngine.visibleStops(snapshot(),it.id).first().name) }
            tap("notify-voice");waitVoice(fixture.messages.getValue("rest"));pause("08-rest-message");tap("input-save")
            waitTag("completion-confirm");pause("09-sent-and-updated");tap("completion-confirm")
            snapshot().members.forEach { assertEquals(fixture.rest.name,JourneyEngine.visibleStops(snapshot(),it.id).first().name) }
            tap("viewer-a");pause("10-dohyun-notification")
            compose.onNodeWithText("예빈이 ${fixture.rest.name} 일정 추가를 신청합니다").assertExists()
            compose.onAllNodesWithText("음성 메시지 듣기").onFirst().performClick();pause("11-listen-to-yebin",true)
            compose.onAllNodesWithTag("reply-voice").onFirst().performScrollTo().performClick()
            waitVoice(fixture.messages.getValue("dohyunReply"));pause("12-dohyun-reply");tap("input-save")
            tap("viewer-b");pause("13-seoyun-notification")
            compose.onNodeWithText("예빈이 ${fixture.rest.name} 일정 추가를 신청합니다").assertExists()
            compose.onAllNodesWithText("음성 메시지 듣기").onFirst().performClick()
            simulate("rest-arrival");pause("14-rest-area-reunion")

            compose.onNodeWithText("게임 제안").performClick()
            type("game-reason",fixture.messages.getValue("gameReason"))
            fixture.members.forEach { tap("player-${it.id}") }
            val routesBeforeGame=snapshot().changes
            compose.onNodeWithTag("game-start").performScrollTo().assertIsEnabled().performClick()
            if (recording) {
                compose.mainClock.autoAdvance=false
                try { repeat(120) { compose.mainClock.advanceTimeBy(20);Thread.sleep(20) } }
                finally { compose.mainClock.autoAdvance=true }
            } else compose.mainClock.advanceTimeBy(3000)
            compose.waitUntil(12000) { snapshot().game.finished }
            assertEquals(fixture.messages["gameWinner"],snapshot().game.winner);assertEquals(routesBeforeGame,snapshot().changes)
            pause("15-dohyun-wins-lunch");tap("close-panel")

            simulate("after-lunch");compose.onNodeWithText("플픽").performClick()
            compose.onNodeWithTag("filter-카페").performScrollTo().performClick();pause("16-cafe-picks")
            compose.onNodeWithText(fixture.cafe.name).performScrollTo().performClick();pause("17-cafe-detail")
            scrollTap("detail-add");tap("insert-last");tap("scope-group")
            assertTrue(JourneyEngine.visibleStops(snapshot(),"me").any { it.name == fixture.cafe.name })
            tap("notify-voice");waitVoice(fixture.messages.getValue("cafe"));pause("18-cafe-message");tap("input-save")
            waitTag("completion-confirm");tap("completion-confirm")
            tap("viewer-b");pause("19-cafe-notification")
            compose.onAllNodesWithTag("reply-voice").onFirst().performScrollTo().performClick()
            waitVoice(fixture.messages.getValue("seoyunReply"));pause("20-seoyun-reply");tap("input-save")
            tap("viewer-me");simulate("cafe-arrival");tap("calendar");pause("21-first-day-memories")
            compose.onNodeWithText(fixture.lunch.name).assertExists()
            compose.onNodeWithText(fixture.rest.name).assertExists()
            compose.onNodeWithText(fixture.cafe.name).assertExists()
            compose.onNodeWithText(fixture.destination.name).assertExists()
            tap("close-panel");simulate("hotel-arrival");pause("22-arrived-together")
            assertEquals(3,snapshot().arrivals.size);assertTrue(snapshot().journeyEnded)

            // Part 3: the same destination context survives the trip → home → discovery transition.
            tap("hub-home");tap("home-picks");compose.onNodeWithText("부산광역시 해운대구").assertExists();pause("23-home-picks")
            compose.onNodeWithTag("filter-카페").performScrollTo().performClick()
            compose.onNodeWithTag("source-filter").performScrollTo().performClick();compose.onNodeWithText("카카오").performClick()
            compose.onNodeWithTag("sort-filter").performScrollTo().performClick();compose.onNodeWithText("별점 높은순").performClick()
            compose.onNodeWithTag("bookmark-waveon").performScrollTo().performClick();assertTrue("waveon" in snapshot().bookmarks)
            pause("24-filtered-bookmarked-picks")
            tap("back");tap("home-trip");waitTag("group-map");pause("25-final-arrival")
        } finally { scenario.close() }
    }
}
