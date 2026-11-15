package com.pleosfriends

import org.junit.Assert.*
import org.junit.Test

class JourneyEngineTest {
    @Test fun `destination heading never treats every hotel as Busan`() {
        assertEquals("서울시 강남구",JourneyEngine.region(TravelState(destination="서울 강남구 호텔")))
        assertEquals("부산광역시 해운대구",JourneyEngine.region(TravelState(destination="부산 호텔")))
        assertEquals("부산광역시 해운대구",JourneyEngine.region(TravelState(destination="파라다이스호텔 부산")))
        assertEquals("대전역 주변",JourneyEngine.region(TravelState(destination="대전역")))
    }
    private fun busanMembers() = listOf(
        Traveler("me","예빈",Place("고양시 일산","경기도 고양시 일산동구",37.6584,126.7689),310,432.0),
        Traveler("a","도현",Place("서울시 마포구","서울특별시 마포구",37.5663,126.9019),285,410.0),
        Traveler("b","서윤",Place("경기도 수원","경기도 수원시",37.2636,127.0286),250,365.0),
    )
    private fun busan() = StoryFixture("busan","대학 동기 부산 원정대",
        Place("부산 호텔","부산광역시 해운대구 해운대해변로 296",35.1595,129.1628),2,540,busanMembers(),
        listOf("01000000001","01000000002"),Place("점심","금강휴게소",36.2816,127.6738),
        Place("죽전휴게소","용인",37.3254,127.1078),Place("해운대 오션카페","해운대",35.1588,129.1756),
        mapOf("rest" to "화장실","dohyunReply" to "좋아! 우리도 잠깐 쉬고 싶었어. 거기서 만나자.",
            "cafe" to "큰 카페","seoyunReply" to "좋아! 부산 도착 기념으로 사진도 찍자.",
            "gameReason" to "오늘 점심 쏘기","gameWinner" to "a"))
    private fun state() = TravelState(destination = "부산 호텔", groupName = "대학 동기 부산 원정대", members = busanMembers(), invited = 2, joined = 2,
        days = listOf(DayPlan(listOf(Place("점심"))), DayPlan(), DayPlan()), nights = 2, story = busan())
    @Test fun `three vehicles can start without a fourth invitation`() { assertTrue(TripRules.start(state()).started) }
    @Test fun `busan day-1 never includes an undecided cafe`() {
        val seeded = JourneyEngine.seed(busan())
        assertEquals(listOf("예빈","도현","서윤"),seeded.members.map { it.name })
        assertTrue(seeded.days.all { it.stops.isEmpty() })
        val planned = TripRules.resize(seeded.copy(destination="부산 호텔",groupName="대학 동기 부산 원정대",invited=2,joined=2),2)
            .copy(days=listOf(DayPlan(listOf(busan().lunch)),DayPlan(),DayPlan()))
        assertEquals(listOf("점심"),planned.days[0].stops.map { it.name })
        assertTrue(planned.days.none { day -> day.stops.any { it.name.contains("카페") } })
        assertTrue(TripRules.start(planned).started)
        val withCafe = JourneyEngine.commit(planned,RouteChange("cafe",busan().cafe,1,"me",RouteScope.GROUP),"커피")
        assertEquals(listOf("점심","해운대 오션카페"),JourneyEngine.visibleStops(withCafe,"me").map { it.name })
    }
    @Test fun `waypoint scope applies immediately without waiting for a voice notification`() {
        val applied = JourneyEngine.commit(state(), RouteChange("one", DemoPlaces.rest, 0, "me", RouteScope.PERSONAL), notify = false)
        assertEquals(listOf("휴게소", "점심"), JourneyEngine.visibleStops(applied, "me").map { it.name })
        assertEquals(listOf("점심"), JourneyEngine.visibleStops(applied, "a").map { it.name })
        assertTrue(applied.notes.isEmpty())
        val notified = JourneyEngine.commit(applied, RouteChange("one", DemoPlaces.rest, 0, "me", RouteScope.PERSONAL), "I'll stop briefly", true)
        assertEquals(applied.changes, notified.changes)
        assertEquals(1, notified.notes.size)
        assertEquals("I'll stop briefly", notified.notes.single().voice)
    }
    @Test fun `personal stop affects only the owner and preserves the original plan`() {
        val initial = state(); val changed = JourneyEngine.commit(initial,RouteChange("one",DemoPlaces.rest,0,"me",RouteScope.PERSONAL),"I'll stop briefly")
        assertEquals(listOf("휴게소","점심"),JourneyEngine.visibleStops(changed,"me").map { it.name })
        assertEquals(listOf("점심"),JourneyEngine.visibleStops(changed,"a").map { it.name })
        assertEquals(JourneyEngine.etaMinutes(initial,"me")+12,JourneyEngine.etaMinutes(changed,"me"))
        assertEquals(JourneyEngine.etaMinutes(initial,"a"),JourneyEngine.etaMinutes(changed,"a"))
        assertEquals(initial.days,changed.days)
    }
    @Test fun `group update reaches every member exactly once`() {
        val s=state();val c=RouteChange("group",DemoPlaces.rest,0,"me",RouteScope.GROUP)
        val updated=JourneyEngine.commit(s,c,"함께 쉬자")
        updated.members.forEach { assertEquals("휴게소",JourneyEngine.visibleStops(updated,it.id).first().name) }
        assertEquals(updated,JourneyEngine.commit(updated,c,"duplicate"))
        assertEquals(listOf("a","b"),updated.notes.single().recipients)
    }
    @Test fun `reply retains original notification and all itinerary rows`() {
        val original=JourneyEngine.commit(state(),RouteChange("r",DemoPlaces.rest,0,"me",RouteScope.GROUP)).copy(viewerId="a")
        val reply=JourneyEngine.reply(original,"좋아, 거기서 만나")
        assertEquals(2,reply.notes.size);assertEquals(original.changes,reply.changes)
        assertEquals("a",reply.notes.last().from);assertTrue("me" in reply.notes.last().recipients)
    }
    @Test fun `game never changes vehicle routes`() {
        val initial=state();val withGame=initial.copy(game=LadderState("점심",listOf("me","a","b"),"a",true))
        assertEquals(JourneyEngine.visibleStops(initial),JourneyEngine.visibleStops(withGame))
        assertEquals(JourneyEngine.etaMinutes(initial),JourneyEngine.etaMinutes(withGame))
    }
    @Test fun `filters sort actual results and preserve bookmarked identity`() {
        val s=state().copy(destination="성수 연무장",picks=PicksFilter(category="카페",sort="가까운순"))
        val rows=PickCatalog.filtered(s)
        assertTrue(rows.all { it.category=="카페" });assertEquals(rows.map { it.distance }.sorted(),rows.map { it.distance })
    }
}
