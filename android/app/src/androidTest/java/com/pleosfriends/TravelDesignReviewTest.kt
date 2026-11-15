package com.pleosfriends

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Focused visual review of the two reference screens; restores the previous saved trip. */
@RunWith(AndroidJUnit4::class)
class TravelDesignReviewTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    @Test fun scheduleThenInvitedFriends() {
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        var original:TravelState?=null
        fun capture(name:String) {
            compose.waitForIdle()
            // Compose's test clock can settle before SurfaceFlinger's transition frame.
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(800)
            var bounds=IntArray(4)
            scenario.onActivity { activity ->
                val view=activity.window.decorView;val point=IntArray(2);view.getLocationOnScreen(point)
                bounds=intArrayOf(point[0],point[1],view.width,view.height)
            }
            val image=instrumentation.uiAutomation.takeScreenshot()!!
            val crop=Bitmap.createBitmap(image,bounds[0],bounds[1],bounds[2],bounds[3])
            val dir=File(instrumentation.targetContext.filesDir,"design-review").apply { mkdirs() }
            File(dir,"$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG,100,it) }
            crop.recycle();image.recycle()
        }
        try {
            scenario.onActivity { activity ->
                original=activity.snapshotForRecording()
                val plans=listOf(DayPlan(listOf(Place("점심"))),DayPlan(),DayPlan())
                activity.presentDesignReview(TravelState(screen=Screen.SETUP,destination="부산 호텔",nights=2,days=plans,
                    groupName="여자들우정영원히",invited=3,joined=3))
            }
            compose.waitUntil(15000) { compose.onAllNodesWithTag("timeline-day-2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("3일차").assertIsDisplayed()
            capture("01-schedule")
            compose.onNodeWithTag("duration").performClick()
            compose.onAllNodesWithText("2박",useUnmergedTree=true).onLast().performClick()
            compose.onNodeWithTag("duration-save").performClick()
            compose.onNodeWithTag("setup-next").assertIsEnabled().performClick()
            compose.onNodeWithText("초대한 친구").assertIsDisplayed()
            listOf("친구 A","친구 B","친구 C").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
            compose.onNodeWithTag("invite-tile").assertExists()
            compose.onNodeWithTag("friends-next").assertIsEnabled()
            capture("02-invited-friends")
            scenario.onActivity { activity -> activity.presentDesignReview(activity.snapshotForRecording().copy(invited=0,joined=0,groupName="")) }
            compose.onNodeWithTag("friends-next").assertIsNotEnabled()
            compose.onNodeWithTag("invite-tile").performClick()
            compose.onNodeWithTag("invite-phone").assertIsDisplayed()
        } finally {
            original?.let { state -> runCatching { scenario.onActivity { it.presentDesignReview(state) } } }
            scenario.close()
        }
    }
}
