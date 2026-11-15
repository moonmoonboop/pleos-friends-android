package com.pleosfriends

import java.util.UUID
import kotlin.math.*

data class Traveler(val id: String, val name: String, val origin: Place, val baseMinutes: Int, val kilometers: Double)
fun defaultTravelers() = listOf(
    Traveler("me", "나", DemoPlaces.home, 29, 5.2),
    Traveler("a", "친구 A", Place("합정", "서울 마포구 합정동", 37.5471, 127.0369), 54, 11.4),
    Traveler("b", "친구 B", Place("잠실", "서울 송파구 잠실동", 37.5168, 127.097), 25, 4.8),
    Traveler("c", "친구 C", Place("석계", "서울 노원구 석계역", 37.5490, 127.0931), 38, 8.1),
)
enum class RouteScope { PERSONAL, GROUP }
data class RouteChange(val id: String = UUID.randomUUID().toString(), val place: Place, val index: Int,
    val ownerId: String, val scope: RouteScope, val addedMinutes: Int = 12, val day: Int = 0)
data class TravelNote(val id: String = UUID.randomUUID().toString(), val from: String, val recipients: List<String>,
    val title: String, val text: String, val voice: String = "", val readBy: List<String> = emptyList())
data class LadderState(val reason: String = "", val participants: List<String> = emptyList(),
    val winner: String = "", val finished: Boolean = false)
data class StoryFixture(val id: String, val groupName: String, val destination: Place, val nights: Int,
    val departureMinute: Int, val members: List<Traveler>, val phones: List<String>, val lunch: Place,
    val rest: Place, val cafe: Place, val messages: Map<String, String>, val sceneHoldMs: Long = 1800, val partySize: Int = 6)

object JourneyEngine {
    fun viewer(state: TravelState) = state.members.firstOrNull { it.id == state.viewerId } ?: state.members.first()
    fun visibleStops(state: TravelState, carId: String = state.viewerId, day: Int = state.activeDay): List<Place> {
        val result = state.days.getOrNull(day)?.stops.orEmpty().toMutableList()
        state.changes.filter { it.day == day && (it.scope == RouteScope.GROUP || it.ownerId == carId) }
            .forEach { result.add(it.index.coerceIn(0, result.size), it.place) }
        return result
    }
    fun etaMinutes(state: TravelState, carId: String = state.viewerId): Int {
        val member = state.members.first { it.id == carId }
        val additions = state.changes.filter { it.day == state.activeDay && (it.scope == RouteScope.GROUP || it.ownerId == carId) }.sumOf { it.addedMinutes }
        return (member.baseMinutes + additions - state.elapsedSeconds / 60).coerceAtLeast(1)
    }
    fun clock(minute: Int): String = "%02d:%02d".format((minute / 60) % 24, minute % 60)
    fun duration(minutes: Int) = if (minutes < 60) "${minutes}분" else "${minutes / 60}시간 ${minutes % 60}분"
    fun legMinutes(state: TravelState, stops: List<Place>, index: Int): Int {
        val from = if (index == 0) viewer(state).origin else stops[index - 1]
        val to = stops[index]
        val lat = Math.toRadians(to.lat - from.lat); val lon = Math.toRadians(to.lon - from.lon)
        val a = sin(lat/2).pow(2) + cos(Math.toRadians(from.lat))*cos(Math.toRadians(to.lat))*sin(lon/2).pow(2)
        val km = 6371 * 2 * atan2(sqrt(a.coerceIn(0.0,1.0)),sqrt((1-a).coerceIn(0.0,1.0)))
        return (km / (if (state.story != null) 75 else 22) * 60).roundToInt().coerceAtLeast(3)
    }
    fun arrivalClock(state: TravelState, carId: String = state.viewerId) = clock(state.departureMinute + state.members.first { it.id == carId }.baseMinutes +
        state.changes.filter { it.scope == RouteScope.GROUP || it.ownerId == carId }.sumOf { it.addedMinutes })
    fun commit(state: TravelState, change: RouteChange, voice: String = "", notify: Boolean = true): TravelState {
        require(change.place.name.isNotBlank() && state.members.any { it.id == change.ownerId })
        val applied = if (state.changes.any { it.id == change.id }) state else state.copy(changes = state.changes + change)
        if (!notify) return applied
        val sender = applied.members.first { it.id == change.ownerId }
        val recipients = applied.members.filter { it.id != change.ownerId }.map { it.id }
        val already = applied.notes.any { it.from == change.ownerId && it.text.contains(change.place.name) }
        if (already) return applied
        val note = TravelNote(from = change.ownerId, recipients = recipients,
            title = if (change.scope == RouteScope.GROUP) "${sender.name}이 ${change.place.name} 일정 추가를 신청합니다" else "${sender.name}이 잠깐 들렀다 갈게요",
            text = if (change.scope == RouteScope.GROUP) "모든 차량의 경로에 ${change.place.name}이 추가됐어요." else "${sender.name}의 차량에만 경유지가 추가됐어요. 다른 차량의 경로는 유지돼요.", voice = voice)
        return applied.copy(notes = applied.notes + note)
    }
    fun reply(state: TravelState, voice: String): TravelState {
        val sender = viewer(state)
        return state.copy(notes = state.notes + TravelNote(from = sender.id, recipients = state.members.filter { it.id != sender.id }.map { it.id },
            title = "${sender.name}의 음성 답장", text = voice, voice = voice))
    }
    fun inbox(state: TravelState) = state.notes.filter { state.viewerId in it.recipients || state.viewerId == it.from }.reversed()
    fun resolve(state: TravelState, text: String, address: String = ""): Place {
        val fixture = state.story
        val found = fixture?.let { listOf(it.destination, it.lunch, it.rest, it.cafe).firstOrNull { p -> p.name == text.trim() } }
        return (found ?: DemoPlaces.resolve(text)).let { if (address.isBlank()) it else it.copy(address = address) }
    }
    fun region(state: TravelState): String = when {
        state.story != null && state.destination.isBlank() -> "부산광역시 해운대구"
        state.destination.contains("강남") -> "서울시 강남구"
        state.destination.contains("부산") -> "부산광역시 해운대구"
        state.destination.contains("수원") -> "경기도 수원시"
        state.destination.contains("제주") -> "제주특별자치도"
        state.destination.contains("성수") || state.destination.contains("연무장") || state.destination.isBlank() -> "서울시 성동구"
        else -> "${state.destination} 주변"
    }
    fun seed(fixture: StoryFixture) = TravelState(members = fixture.members, story = fixture, departureMinute = fixture.departureMinute)
    /** Test-only travel simulation. The demo never sends these actions to the vehicle. */
    fun simulate(state: TravelState, action: String, argument: String): TravelState = when (action) {
        "showcase" -> TripRules.start(TravelState(destination="성수 연무장",groupName="여자들우정영원히",nights=2,
            days=listOf(DayPlan(listOf(DemoPlaces.rest,DemoPlaces.river)),DayPlan(),DayPlan()),invited=3,joined=3))
        "viewer" -> state.copy(viewerId = argument.also { id -> require(state.members.any { it.id == id }) }, panel = Panel.NOTIFICATIONS)
        "rest-arrival" -> state.copy(viewerId = state.members.first().id, panel = Panel.NONE,
            completedStops = (state.completedStops + listOfNotNull(state.story?.rest?.name)).distinct())
        "after-lunch" -> state.copy(viewerId = state.members.first().id, panel = Panel.NONE,
            completedStops = (state.completedStops + listOfNotNull(state.story?.rest?.name, state.story?.lunch?.name)).distinct())
        "cafe-arrival" -> state.copy(completedStops = (state.completedStops + listOfNotNull(state.story?.cafe?.name)).distinct(), panel = Panel.NONE)
        "hotel-arrival" -> state.copy(arrivals = state.members.map { it.id }, panel = Panel.NONE, journeyEnded = true)
        else -> state
    }
}
