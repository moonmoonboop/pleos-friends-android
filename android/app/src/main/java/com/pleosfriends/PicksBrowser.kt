package com.pleosfriends

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class PicksFilter(val category: String = "전체", val source: String = "네이버", val sort: String = "플픽 추천순", val basis: String = "실시간", val collection: String = "")
data class PickPlace(val id: String, val place: Place, val category: String, val summary: String, val hours: String,
    val rating: Double, val distance: Int, val image: Int, val visits: Int = 128, val likes: Int = 30,
    val goneTag: String = "1.3만", val likeTag: String = "3,481")

internal object PickCatalog {
    fun list(state: TravelState): List<PickPlace> {
        val busan = JourneyEngine.region(state).startsWith("부산") && (state.picks.basis == "도착지" || state.story == null || state.completedStops.isNotEmpty())
        if (busan) return listOf(
            PickPlace("waveon", state.story?.cafe ?: Place("해운대 오션카페", "부산광역시 해운대구 달맞이길 21", 35.1588,129.1756), "카페",
                "식물 사이에서 커피 한 잔. 잠깐 들렀다 가기 좋아요.", "10:00–21:00", 4.6, 1700, R.drawable.pick_ocean, 128, 30, "1.3만", "3,481"),
            PickPlace("busan-food", Place("부산 바다식탁 · 시연", "부산광역시 해운대구 달맞이길 30 · 시연 장소",35.162,129.18), "식당", "여섯 명이 둘러앉아 즐기는 부산의 한 끼", "11:00–21:00 · 시연",4.6,1200,R.drawable.pick_food),
            PickPlace("busan-park", Place("해운대 해변", "부산광역시 해운대구 해운대해변로",35.158,129.16), "여행/명소", "부산 도착 기념, 함께 남기는 한 장", "상시 개방 · 시연",4.7,1600,R.drawable.pick_ocean),
            PickPlace("busan-cafe", Place("해운대 포레스트 카페 · 시연", "부산광역시 해운대구 중동 · 시연 장소",35.17,129.18),"카페","넓은 테이블에서 쉬어가는 오후","09:00–22:00 · 시연",4.4,650,R.drawable.home_picks))
        return listOf(
            PickPlace("roasters", Place("연무장 로스터스", "서울 성동구 연무장길 17 · 시연 장소",37.5449,127.056),"카페","연무장길 로스터리, 창가가 넓어요.","08:00–22:00 · 시연",4.8,450,R.drawable.home_picks),
            PickPlace("happy",DemoPlaces.cafe,"카페","식물 사이에서 커피 한 잔, 잠깐 들렀다 가기 좋아요.","10:00–21:00 · 시연",4.6,500,R.drawable.home_picks),
            PickPlace("park",Place("옥수 한강공원","서울 성동구 옥수동 한강공원",37.5346,127.0215),"여행/명소","강변 산책과 화장실, 10분 쉬기도 좋아요.","상시 개방",4.7,2100,R.drawable.home_journey),
            PickPlace("bakery",Place("성수 베이커리","서울 성동구 성수동2가 · 시연 장소",37.544,127.058),"카페","빵과 커피를 같이 가져가기 좋아요.","09:00–20:00 · 시연",4.4,720,R.drawable.pick_food),
            PickPlace("brunch",Place("서울숲 브런치","서울 성동구 서울숲길 36 · 시연 장소",37.547,127.044),"식당","아이와 함께, 여유롭게 나누는 한 끼","10:00–20:00 · 시연",4.5,1400,R.drawable.pick_food),
            PickPlace("forest",Place("서울숲","서울 성동구 뚝섬로 273",37.5445,127.0374),"여행/명소","함께 걷고 사진을 남기는 초록빛 휴식","상시 개방 · 시연",4.9,1700,R.drawable.home_journey))
    }
    fun filtered(state: TravelState): List<PickPlace> {
        val f = state.picks
        val rows = list(state).filter { (f.category == "전체" || it.category == f.category) &&
            (f.collection != "아이와 함께" || it.category != "카페") }
        return when (f.sort) {
            "별점 높은순" -> rows.sortedByDescending { it.rating }
            "가까운순" -> rows.sortedBy { it.distance + if (f.basis == "도착지") 700 else 0 }
            else -> rows.sortedByDescending { it.likes * 10 + it.rating }
        }
    }
}

@Composable internal fun StandalonePicks(store: TripStore, back: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header(JourneyEngine.region(store.state), "Pleos Picks  ·  우리 취향이 만나는 곳", back)
        PicksBrowser(store, Modifier.weight(1f), false, null)
    }
}

@Composable internal fun PicksBrowser(store: TripStore, modifier: Modifier, compact: Boolean, add: ((Place) -> Unit)?) {
    val state = store.state; val filter = state.picks
    var detail by remember { mutableStateOf<PickPlace?>(null) }
    fun update(value: PicksFilter) = store.update { it.copy(picks = value) }
    if (detail != null) {
        val p = detail!!
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            TextButton({ detail = null }) { Label("‹ 목록으로", 24, FontWeight.Bold, Purple) }
            Image(painterResource(p.image), null, Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(25.dp)), contentScale = ContentScale.Crop)
            Label(p.place.name, 34, FontWeight.Bold); Label(p.summary, 24, color = Muted)
            Label("위치  ${p.place.address}", 23, maxLines = 1); Label("영업시간  ${p.hours}", 23, maxLines = 1)
            Label("★ ${p.rating}  ·  ${filter.source} 지도", 24, FontWeight.Bold, Purple, maxLines = 1)
            HyundaiOwnerPill(p.visits, p.likes)
            if (add != null) Action("일정 추가", { add(p.place) }, Modifier.fillMaxWidth().testTag("detail-add"))
            Label("사진·방문·추천·별점·거리 데이터는 시연용입니다.", 17, color = Muted)
        }
        return
    }
    LazyColumn(modifier.testTag("picks-list"), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Label(if (compact) JourneyEngine.region(state) else "curation", if (compact) 20 else 38, FontWeight.Bold)
            if (filter.collection.isNotBlank()) TextButton({ update(filter.copy(collection = "")) }) { Label("‹ ${filter.collection} 컬렉션 · 전체 보기", 20, color = Purple) }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(Triple("현재 진행 중인 지역 축제", R.drawable.pick_festival, "지역 축제"),
                    Triple(if (JourneyEngine.region(state).startsWith("부산")) "아이와 함께 · 해운대" else "아이와 함께 · 서울숲",R.drawable.home_journey,"아이와 함께"),
                    Triple("잠깐 쉬어가는 카페", R.drawable.home_picks,"카페")).forEach { (title, image, collection) ->
                    Box(Modifier.width(if (compact) 300.dp else 780.dp).height(if (compact) 170.dp else 225.dp).clip(RoundedCornerShape(23.dp)).clickable { update(filter.copy(collection = collection, category = if (collection == "카페") "카페" else "전체")) }) {
                        Image(painterResource(image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(.30f)))
                        Label(title, if (compact) 23 else 31, FontWeight.Bold, Color.White, Modifier.align(Alignment.BottomStart).padding(22.dp))
                    }
                }
            }
        }
        item {
            Label("장소 찾기", 28, FontWeight.Bold, modifier = Modifier.padding(bottom = 13.dp))
            if (compact) {
                Row(Modifier.fillMaxWidth().background(Color(0xFFE8E9EF), RoundedCornerShape(18.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("실시간" to "현재 위치", "도착지" to "도착지").forEach { (basis, label) ->
                        SelectChip(label, filter.basis == basis, Modifier.weight(1f).testTag("basis-$basis")) { update(filter.copy(basis = basis)) }
                    }
                }
                Label(if (filter.basis == "실시간") "내 차량 주변" else "${state.destination.ifBlank { JourneyEngine.region(state) }} 주변", 18, color = Muted, modifier = Modifier.padding(vertical = 12.dp))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("전체", "카페", "식당", "여행/명소").forEach { category -> SelectChip(category, filter.category == category, Modifier.testTag("filter-$category")) { update(filter.copy(category = category)) } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownChoice(filter.source, listOf("네이버", "카카오"), Modifier.weight(1f).testTag("source-filter")) { update(filter.copy(source = it)) }
                DropdownChoice(filter.sort, listOf("별점 높은순", "가까운순", "플픽 추천순"), Modifier.weight(1.4f).testTag("sort-filter")) { update(filter.copy(sort = it)) }
            }
        }
        item { Label("현대차 유저들이 픽한 장소", if (compact) 25 else 30, FontWeight.Bold) }
        items(PickCatalog.filtered(state), key = { it.id }) { p ->
            PickRow(p, state, compact, { detail = p }, {
                store.update { it.copy(bookmarks = if (p.id in it.bookmarks) it.bookmarks - p.id else it.bookmarks + p.id) }
            }, add?.let { action -> { action(p.place) } })
        }
        item { Label("시연 데이터 · 실시간 지도 검색·광고·실제 현대차 방문 통계와 연결되지 않습니다.", 17, color = Muted) }
    }
}

@Composable private fun PickRow(p: PickPlace, state: TravelState, compact: Boolean, detail: () -> Unit, bookmark: () -> Unit, add: (() -> Unit)?) {
    val meters = p.distance + if (state.picks.basis == "도착지") 700 else 0
    val nearby = if (meters >= 1000) "근처 ${String.format(java.util.Locale.US, "%.1f", meters / 1000f)}km" else "근처 ${meters}m"
    Row(Modifier.fillMaxWidth().shadow(2.dp,RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color.White)
        .padding(if (compact) 14.dp else 20.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp)) {
        Box(Modifier.width(if (compact) 148.dp else 220.dp).height(if (compact) 168.dp else 210.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = detail)) {
            Image(painterResource(p.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Column(Modifier.align(Alignment.TopStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PhotoTag("#다녀간 ${p.goneTag}"); PhotoTag("#좋아요 ${p.likeTag}")
            }
            IconButton(bookmark, Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp).background(Color.White.copy(.92f), CircleShape).testTag("bookmark-${p.id}")) {
                Icon(if (p.id in state.bookmarks) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "북마크", tint = Purple, modifier = Modifier.size(18.dp))
            }
            Label(nearby, 14, FontWeight.Bold, Color.White,
                Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0xFFE07827), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Label(p.place.name, if (compact) 22 else 28, FontWeight.Bold, modifier = Modifier.weight(1f).clickable(onClick = detail), maxLines = 1)
                if (add != null) Surface(onClick = add, shape = RoundedCornerShape(16.dp), color = Purple, modifier = Modifier.testTag("pick-add-${p.id}")) {
                    Label("일정 추가", 16, FontWeight.Bold, Color.White, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), maxLines = 1)
                }
            }
            Label(p.summary, if (compact) 16 else 20, color = Muted, modifier = Modifier.padding(top = 6.dp).clickable(onClick = detail), maxLines = 2)
            HyundaiOwnerPill(p.visits, p.likes, Modifier.padding(top = 10.dp))
            Label(p.place.address, if (compact) 15 else 18, color = Muted, modifier = Modifier.padding(top = 8.dp), maxLines = 1)
            Label(p.hours, if (compact) 15 else 18, color = Muted, maxLines = 1)
            Label("${p.rating} · ${state.picks.source} 지도", if (compact) 15 else 18, color = Muted, maxLines = 1)
        }
    }
}
@Composable private fun PhotoTag(text: String) {
    Label(text, 12, FontWeight.Bold, Color.White, Modifier.background(Color.Black.copy(.45f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp), maxLines = 1)
}
@Composable private fun HyundaiOwnerPill(visits: Int, likes: Int, modifier: Modifier = Modifier) {
    Row(modifier.background(Color(0xFF003478), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
        .semantics { contentDescription = "현대차 이용자 ${visits}명 방문 · ${likes}명 추천" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(16.dp, 11.dp)) {
            drawOval(Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
            drawLine(Color.White, Offset(size.width * .38f,size.height * .25f),Offset(size.width * .27f,size.height * .8f),1.8.dp.toPx())
            drawLine(Color.White, Offset(size.width * .7f,size.height * .2f),Offset(size.width * .6f,size.height * .75f),1.8.dp.toPx())
            drawLine(Color.White,Offset(size.width * .33f,size.height * .5f),Offset(size.width * .65f,size.height * .5f),1.8.dp.toPx())
        }
        Label("$visits", 14, FontWeight.Bold, Color.White, maxLines = 1)
        Icon(Icons.Rounded.ThumbUp, null, Modifier.size(12.dp), tint = Color.White)
        Label("$likes", 14, FontWeight.Bold, Color.White, maxLines = 1)
    }
}
@Composable internal fun SelectChip(label: String, selected: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Surface(onClick = click, shape = RoundedCornerShape(18.dp), color = if (selected) Purple else Color.White, modifier = modifier.heightIn(min = 54.dp), border = if (selected) null else BorderStroke(1.dp, Line)) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { Label(label, 21, FontWeight.Bold, if (selected) Color.White else Ink) }
    }
}
@Composable private fun DropdownChoice(value: String, options: List<String>, modifier: Modifier, select: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(18.dp), color = Color.White,border=BorderStroke(1.dp,Line)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Label(value, 21, FontWeight.Bold, modifier = Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null) }
        }
        DropdownMenu(open, { open = false }) { options.forEach { option -> DropdownMenuItem(text = { Label(option, 24) }, onClick = { select(option); open = false }) } }
    }
}
