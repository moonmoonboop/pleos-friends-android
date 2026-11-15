package com.pleosfriends

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

private data class Geo(val lon: Double, val lat: Double)
private data class Road(val kind: String, val points: List<Geo>)
private data class Area(val kind: String, val rings: List<List<Geo>>)
private data class MapData(val bounds: List<Double>, val roads: List<Road>, val areas: List<Area>,
    val routes: List<List<Geo>>, val goal: Geo)

@Composable internal fun Hub(store: TripStore, services: ConnectServices, voiceOverlay:Boolean, request: (InputRequest) -> Unit, home: () -> Unit, complete: (String) -> Unit) {
    val state = store.state
    var picked by remember { mutableStateOf<Place?>(null) }
    var revised by remember { mutableStateOf(false) }
    var etaCardSize by remember { mutableStateOf(IntSize.Zero) }
    fun open(panel: Panel) { if(panel==Panel.WAYPOINT) revised=false;store.update { it.copy(panel = if (it.panel == panel) Panel.NONE else panel) } }
    LaunchedEffect(services.restricted) { if (services.restricted && store.state.panel in listOf(Panel.GAME,Panel.PICKS,Panel.WAYPOINT)) store.update { it.copy(panel = Panel.NONE) } }
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val panelWidth=(maxWidth-18.dp)*.41f
        Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(if(state.panel!=Panel.NONE&&!voiceOverlay) 18.dp else 0.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(32.dp)).background(Color(0xFFDADBDD))) {
                GroupMap(state,Modifier.fillMaxSize(),etaCardSize)
                Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Surface(onClick=home,shape=CircleShape,color=Color.White,modifier=Modifier.size(52.dp).testTag("hub-home")) {
                            Box(contentAlignment=Alignment.Center) { Icon(Icons.Rounded.Home,"홈",Modifier.size(24.dp),tint=Purple) }
                        }
                        Surface(color=Color.White.copy(.97f),shape=RoundedCornerShape(21.dp),modifier=Modifier.weight(1f)) {
                            val heading = if (state.journeyEnded) "도착" else "${TripRules.nextPlace(state).name} 가는 중"
                            Label("${state.groupName} | ${state.activeDay+1}일차 | $heading",20,FontWeight.Bold,
                                modifier=Modifier.padding(horizontal=16.dp,vertical=14.dp),maxLines=1)
                        }
                        HeaderIcon(Icons.Rounded.CalendarMonth,"여행 일정","calendar") { open(Panel.ITINERARY) }
                        HeaderIcon(Icons.Rounded.NotificationsNone,"알림${JourneyEngine.inbox(state).size.takeIf { it>0 }?.let { " $it" } ?: ""}","notifications") { open(Panel.NOTIFICATIONS) }
                    }
                }
                Surface(color=Color.White.copy(.98f),shape=RoundedCornerShape(22.dp),modifier=Modifier.align(Alignment.BottomStart).padding(start=22.dp,bottom=22.dp)
                    .shadow(3.dp,RoundedCornerShape(22.dp)).onSizeChanged { etaCardSize=it }) {
                    Column(Modifier.padding(20.dp)) {
                        Label(if(state.journeyEnded) "모두 도착했어요" else "${JourneyEngine.viewer(state).name} · 최종 도착까지",17,color=Muted,maxLines=1)
                        Label(if(state.journeyEnded) "${state.members.size}/${state.members.size}" else "약 ${JourneyEngine.duration(JourneyEngine.etaMinutes(state))}",28,FontWeight.Bold)
                        Label(state.destination,20,color=Purple,maxLines=1)
                        Label(if(state.journeyEnded) "© OpenStreetMap" else "예상 도착 ${JourneyEngine.arrivalClock(state)}",17,color=Muted,maxLines=1)
                    }
                }
                if(state.panel==Panel.NONE||voiceOverlay) Column(Modifier.align(Alignment.CenterEnd).padding(end=20.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Rail(Icons.Rounded.AddLocationAlt,"경유 추가",services.policyKnown&&!services.restricted,"waypoint-add") { picked=null;open(Panel.WAYPOINT) }
                    Rail(Icons.Rounded.Casino,"게임 제안",services.policyKnown&&!services.restricted,"game-open") { open(Panel.GAME) }
                    Rail(Icons.Rounded.AutoAwesome,"플픽",services.policyKnown&&!services.restricted,"picks-open") { open(Panel.PICKS) }
                }
            }
            // Keep the editor composed while a voice dialog expands the map underneath it.
            // Scope already applied the route; cancelling voice only skips the notification.
            if(state.panel!=Panel.NONE) Box(Modifier.width(if(voiceOverlay) 0.dp else panelWidth).fillMaxHeight().clipToBounds()) {
                Column(Modifier.requiredWidth(panelWidth).fillMaxHeight().clip(RoundedCornerShape(30.dp)).background(Paper).padding(18.dp).testTag("hub-panel")) {
                    Row(Modifier.fillMaxWidth().padding(bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Label(when(state.panel) { Panel.ITINERARY->"전체 여행 일정";Panel.NOTIFICATIONS->"알림";Panel.WAYPOINT->if(revised) "수정된 일정" else "일정 추가";Panel.GAME->"게임 제안";else->"Pleos Curation" },29,FontWeight.Bold,modifier=Modifier.weight(1f))
                        IconButton({open(state.panel)},Modifier.size(52.dp).testTag("close-panel")) { Icon(Icons.Rounded.Close,"패널 닫기") }
                    }
                    when(state.panel) {
                        Panel.ITINERARY->TravelItinerary(state,Modifier.weight(1f))
                        Panel.NOTIFICATIONS->NotificationsPanel(store,services,request)
                        Panel.WAYPOINT->RealtimeSchedule(store,picked,services,request,complete,{revised=it}) { open(Panel.WAYPOINT) }
                        Panel.GAME->LadderPanel(store,services,request)
                        Panel.PICKS->PicksBrowser(store,Modifier.fillMaxSize(),true) {place->picked=place;open(Panel.WAYPOINT)}
                        else->Unit
                    }
                }
            }
        }
    }
}
@Composable private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, click: () -> Unit) {
    Surface(onClick = click, shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation=1.dp, modifier = Modifier.testTag(tag)) {
        Row(Modifier.height(52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = Purple); Spacer(Modifier.width(8.dp)); Label(label, 18, FontWeight.Bold, maxLines = 1)
        }
    }
}
@Composable private fun Rail(icon: androidx.compose.ui.graphics.vector.ImageVector,label:String,enabled:Boolean=true,tag:String,click:()->Unit) {
    Surface(onClick=click,enabled=enabled,shape=CircleShape,color=Color.White,shadowElevation=2.dp,modifier=Modifier.size(88.dp).testTag(tag)) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Icon(icon,label,Modifier.size(22.dp),tint=if(enabled) Purple else Muted)
            Text(label,color=if(enabled) Ink else Muted,fontFamily=TravelReviewFont,fontWeight=FontWeight.Bold,fontSize=13.sp,lineHeight=15.sp,
                textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1)
        }
    }
}

@Composable private fun GroupMap(state: TravelState, modifier: Modifier,etaCardSize:IntSize) {
    val context = LocalContext.current
    val hasStop = state.days[0].stops.isNotEmpty()
    val isBusan = state.story != null
    val data = remember(hasStop, state.changes, isBusan) {
        val root = JSONObject(context.assets.open(if (isBusan) "korea-map.json" else "seongsu-map.json").bufferedReader().use { it.readText() })
        fun points(a: JSONArray): List<Geo> = (0 until a.length()).map { i -> a.getJSONArray(i).let { Geo(it.getDouble(0), it.getDouble(1)) } }
        MapData((0..3).map { root.getJSONArray("bbox").getDouble(it) },
            root.getJSONArray("roads").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { Road(it.getString("kind"), points(it.getJSONArray("points"))) } } },
            root.getJSONArray("areas").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { area ->
                Area(area.getString("kind"), area.getJSONArray("rings").let { rings -> (0 until rings.length()).map { points(rings.getJSONArray(it)) } }) } } },
            state.members.map { member ->
                val changed = state.changes.any { it.scope == RouteScope.GROUP || it.ownerId == member.id }
                val routes = root.getJSONObject("routes").getJSONObject(member.id)
                points(routes.getJSONArray(if (state.story != null && state.changes.any { (it.scope==RouteScope.GROUP||it.ownerId==member.id)&&it.place.name==state.story.cafe.name } && routes.has("cafe")) "cafe" else if (changed && routes.has("changed")) "changed" else if (hasStop) "stop" else "direct"))
            },
            root.getJSONArray(if (hasStop) "stop" else "destination").let { Geo(it.getDouble(0), it.getDouble(1)) })
    }
    val busanRegion=isBusan&&state.completedStops.contains(state.story?.lunch?.name)
    val raster=remember(isBusan,busanRegion) { if(isBusan) context.assets.open("maps/${if(busanRegion) "busan" else "metro"}.webp").use {BitmapFactory.decodeStream(it).asImageBitmap()} else null }
    val rasterBounds=if(busanRegion) listOf(128.94,35.08,129.36,35.40) else listOf(126.35,37.02,127.55,37.88)
    var zoom by remember(busanRegion) { mutableFloatStateOf(1f) }; var pan by remember(busanRegion) { mutableStateOf(Offset.Zero) }
    Canvas(modifier.testTag("group-map").pointerInput(busanRegion) { detectTransformGestures { _, move, scale, _ -> zoom = (zoom * scale).coerceIn(.8f, 2.7f); pan += move } }) {
        val bounds = if(isBusan) rasterBounds else data.bounds
        val centerLon = (bounds[0] + bounds[2]) / 2; val centerLat = (bounds[1] + bounds[3]) / 2
        val cosLat = cos(Math.toRadians(centerLat))
        val scale = min(size.width / ((bounds[2] - bounds[0]) * cosLat), size.height / (bounds[3] - bounds[1])).toFloat() * (if(isBusan) 1.35f else .9f) * zoom
        fun project(p: Geo) = Offset(size.width / 2 + ((p.lon - centerLon) * cosLat * scale).toFloat(), size.height / 2 - ((p.lat - centerLat) * scale).toFloat()) + pan
        fun path(points: List<Geo>) = Path().apply { points.forEachIndexed { i, geo -> val p = project(geo); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        if(raster!=null) {
            drawRect(Color(0xFFDADBDD))
            val topLeft=project(Geo(rasterBounds[0],rasterBounds[3]));val bottomRight=project(Geo(rasterBounds[2],rasterBounds[1]))
            drawImage(raster,dstOffset=IntOffset(topLeft.x.roundToInt(),topLeft.y.roundToInt()),
                dstSize=IntSize((bottomRight.x-topLeft.x).roundToInt().coerceAtLeast(1),(bottomRight.y-topLeft.y).roundToInt().coerceAtLeast(1)),filterQuality=FilterQuality.Medium)
        } else {
            drawRect(Color(0xFFD8DADD))
            data.areas.forEach {a->a.rings.forEach {ring->drawPath(path(ring).apply {close()},when(a.kind) {"water"->Color(0xFFB7BFC7);else->Color(0xFFC8CECB)})}}
            data.roads.forEach {road->
                val major=road.kind in listOf("primary","secondary","trunk","motorway")
                if(major) drawPath(path(road.points),Color(0xFFA8ADB3),style=Stroke(7*density,cap=StrokeCap.Round))
                drawPath(path(road.points),Color.White,style=Stroke((if(major) 4.5f else 1.8f)*density*sqrt(zoom),cap=StrokeCap.Round))
            }
        }
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {typeface=Typeface.create("sans-serif",Typeface.BOLD);textAlign=Paint.Align.CENTER;textSize=18*density}
        val times = state.members.map { JourneyEngine.etaMinutes(state,it.id) }
        data.routes.forEachIndexed { i, route ->
            drawPath(path(route), Color.White.copy(.95f), style = Stroke(11 * density, cap = StrokeCap.Round))
            drawPath(path(route), CarColors[i], style = Stroke(6 * density, cap = StrokeCap.Round))
        }
        val placedLabels = mutableListOf(
            Rect(0f,0f,size.width,130*density),
            Rect(0f,size.height-etaCardSize.height-125*density,etaCardSize.width+44*density,size.height),
            Rect(size.width-150*density,size.height/2-215*density,size.width,size.height/2+215*density))
        data.routes.forEachIndexed { i, route ->
            val progress = when {
                state.journeyEnded -> 1f
                isBusan && state.completedStops.contains(state.story?.cafe?.name) -> .985f
                isBusan && state.completedStops.contains(state.story?.lunch?.name) -> .93f
                isBusan && state.completedStops.contains(state.story?.rest?.name) -> .16f
                else -> (state.elapsedSeconds / (times[i] * 60f)).coerceIn(0f,.9f)
            }
            val actualPoint = when {
                state.journeyEnded && state.story != null -> Geo(state.story.destination.lon,state.story.destination.lat)
                state.story != null && state.completedStops.contains(state.story.cafe.name) -> Geo(state.story.cafe.lon,state.story.cafe.lat)
                state.story != null && state.completedStops.contains(state.story.rest.name) && !state.completedStops.contains(state.story.lunch.name) -> Geo(state.story.rest.lon,state.story.rest.lat)
                busanRegion -> route.filter {it.lon in 128.94..129.36&&it.lat in 35.08..35.40}.let {local->
                    if(local.isNotEmpty()) local[((.12f+i*.21f)*local.lastIndex).toInt()] else route.last()
                }
                else -> route[(progress * route.lastIndex).toInt().coerceIn(0, route.lastIndex)]
            }
            val point = project(actualPoint)
            drawCircle(Color.White, 23 * density, point); drawCircle(CarColors[i], 17 * density, point); drawCircle(Color.White, 6 * density, point)
            val remaining = times[i]
            val km = String.format(java.util.Locale.KOREA, "%.1f", max(.3, state.members[i].kilometers * (1 - progress)))
            val label = if (state.members[i].id in state.arrivals) "${state.members[i].name} · 도착" else "${state.members[i].name} ${JourneyEngine.arrivalClock(state,state.members[i].id)} · ${km}km"
            paint.color = Color.White.toArgb(); paint.textSize = 18 * density
            val width = paint.measureText(label) + 40 * density
            val x = point.x.coerceIn(width / 2 + 12, (size.width - width / 2 - 12).coerceAtLeast(width / 2 + 12))
            var y = (point.y - (48 + if (isBusan) i * 45 else 0) * density).coerceIn(40 * density, size.height - 45 * density)
            fun labelBounds(centerY: Float) = Rect(x - width / 2, centerY - 23 * density, x + width / 2, centerY + 20 * density)
            // Nearby departure cities and shared stops must still show every car's ETA.
            val markerBounds=Rect(point.x-29*density,point.y-29*density,point.x+29*density,point.y+29*density)
            fun labelCollision(centerY:Float)=placedLabels.any {it.inflate(8*density).overlaps(labelBounds(centerY))}||markerBounds.overlaps(labelBounds(centerY))
            if (labelCollision(y)) {
                val candidates = (1..20).flatMap { step -> listOf(y + step * 54 * density, y - step * 54 * density) }
                y = candidates.firstOrNull { candidate ->
                    candidate >= 40 * density && candidate <= size.height - 45 * density &&
                        !labelCollision(candidate)
                } ?: y
            }
            val labelRect = labelBounds(y)
            placedLabels += labelRect
            drawRoundRect(Color(0xFF45484F).copy(.94f), Offset(x - width / 2, y - 23 * density), Size(width, 43 * density), CornerRadius(14 * density))
            drawCircle(CarColors[i],5*density,Offset(labelRect.left+12*density,y-1*density))
            drawContext.canvas.nativeCanvas.drawText(label, x+6*density, y + 6 * density, paint)
        }
        val goalPlace = TripRules.nextPlace(state)
        val goal = project(if (isBusan) Geo(goalPlace.lon,goalPlace.lat) else data.goal)
        if(goal.x in 30*density..size.width-30*density && goal.y in 140*density..size.height-85*density) {
        drawCircle(Purple.copy(.12f), 39 * density, goal); drawCircle(Color.White, 22 * density, goal)
        drawCircle(Purple, 17 * density, goal, style = Stroke(6 * density)); drawCircle(Color.White, 7 * density, goal)
        }
    }
}
