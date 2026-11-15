package com.pleosfriends

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.UUID

@Composable internal fun TravelItinerary(state: TravelState, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Label("${JourneyEngine.viewer(state).name}의 일정", 22, color = Muted)
        state.days.forEachIndexed { day, _ ->
            Label("${day + 1}일차", 27, FontWeight.Bold, Purple, Modifier.padding(top = 10.dp))
            ScheduleRow("출발", if (day == 0) JourneyEngine.viewer(state).origin.name else "전날 숙소", JourneyEngine.clock(state.departureMinute))
            val dayStops = JourneyEngine.visibleStops(state, day = day)
            dayStops.forEachIndexed { index, place ->
                val changed = state.changes.any { it.place.name == place.name && (it.ownerId == state.viewerId || it.scope == RouteScope.GROUP) }
                ScheduleRow("stop ${index + 1}", place.name, "+${JourneyEngine.legMinutes(state,dayStops,index)}분", highlight = changed,
                    done = place.name in state.completedStops)
            }
            ScheduleRow("도착", state.destination, "${JourneyEngine.arrivalClock(state)} 예상", done = state.journeyEnded)
        }
    }
}

@Composable internal fun ScheduleRow(kind: String, name: String, time: String, highlight: Boolean = false, done: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).shadow(if(highlight) 3.dp else 1.dp,RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
        .background(if (highlight) Color(0xFFF4F1FF) else Color.White)
        .then(if (highlight) Modifier.border(1.5.dp, Purple.copy(.55f), RoundedCornerShape(18.dp)) else Modifier)
        .padding(horizontal = 17.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(if (done) "✓ $kind" else kind, 18, FontWeight.Bold, if (highlight) Purple else Muted, Modifier.width(72.dp), maxLines = 1)
        Label(name, 23, FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 9.dp), maxLines = 1)
        Label(time, 19, FontWeight.Bold, if (highlight) Purple else Ink, maxLines = 1)
    }
}

@Composable internal fun RealtimeSchedule(store: TripStore, picked: Place?, services: ConnectServices,
    request: (InputRequest) -> Unit, complete: (String) -> Unit, previewChanged:(Boolean)->Unit, back:()->Unit) {
    val state = store.state
    var query by remember(picked) { mutableStateOf(picked?.name.orEmpty()) }
    var candidate by remember(picked) { mutableStateOf<Place?>(picked) }
    var insertion by remember(picked) { mutableStateOf<Int?>(null) }
    var scope by remember { mutableStateOf<RouteScope?>(null) }
    LaunchedEffect(insertion) {previewChanged(insertion!=null)}
    val changeId = remember { UUID.randomUUID().toString() }
    val zones = remember { mutableStateMapOf<Int, Rect>() }
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val stops = JourneyEngine.visibleStops(state)
    fun finish() {
        dragPoint?.let { point -> insertion = zones.minByOrNull { (_, r) -> kotlin.math.abs(r.center.y - point.y) }?.key?.coerceIn(0, stops.size) }
        dragPoint = null
    }
    fun changeOf(selected: RouteScope): RouteChange? {
        val p = candidate ?: return null; val position = insertion ?: return null
        return RouteChange(id = changeId, place = p, index = position, ownerId = state.viewerId, scope = selected, day = state.activeDay)
    }
    fun applyScope(selected: RouteScope) {
        if (scope != null) return
        val change = changeOf(selected) ?: return
        scope = selected
        store.update { JourneyEngine.commit(it, change, notify = false).copy(panel = Panel.WAYPOINT) }
        services.startNavigation(store.state)
        services.announce(if (selected == RouteScope.GROUP) "모든 차량 경로에 반영했어요" else "내 차 경로에만 반영했어요")
    }
    fun finish(voice: String, notify: Boolean) {
        val selected = scope ?: return
        val change = changeOf(selected) ?: return
        val p = change.place
        store.update { JourneyEngine.commit(it, change, voice, notify).copy(panel = Panel.NONE) }
        services.startNavigation(store.state)
        services.announce(if (notify) "친구들에게 알림을 보냈어요" else "여행 일정 수정이 완료됐어요")
        val count = if (selected == RouteScope.GROUP) "${store.state.members.size}대 전체 경로" else "내 차 경로"
        complete("✓  Sent and updated\n${if (notify) "친구들에게 알림을 보냈어요" else "여행 일정을 수정했어요"}\n$count · ${p.name}\n예상 도착 ${JourneyEngine.arrivalClock(store.state)}")
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        if(insertion==null) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(query,{query=it;candidate=if(it.isBlank()) null else JourneyEngine.resolve(state,it);insertion=null},
                Modifier.weight(1f).testTag("realtime-query"),placeholder={Label("주소 또는 장소 검색",23,color=Muted)},singleLine=true,textStyle=textStyle(),shape=RoundedCornerShape(20.dp))
            OutlinedButton({request(InputRequest("Gleo AI · 추가할 장소",query,state.story?.rest?.name?:"가평제알휴게소",true) {
                query=it;candidate=JourneyEngine.resolve(store.state,it);insertion=null
            })},Modifier.height(56.dp).testTag("realtime-gleo"),shape=RoundedCornerShape(18.dp),
                contentPadding=PaddingValues(horizontal=14.dp)) {
                Icon(Icons.Rounded.AutoAwesome,"Gleo AI",Modifier.size(20.dp),tint=Purple)
                Spacer(Modifier.width(6.dp));Label("Gleo AI",18,FontWeight.Bold,Purple,maxLines=1)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ScheduleRow("출발",JourneyEngine.viewer(state).origin.name,JourneyEngine.clock(state.departureMinute))
            if(candidate!=null&&insertion==null) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Purple).testTag("realtime-candidate")
                    .onGloballyPositioned {bounds=it.boundsInRoot()}
                    .pointerInput(query) {detectDragGestures(onDragStart={dragPoint=bounds.topLeft+it},
                        onDrag={change,_->change.consume();dragPoint=bounds.topLeft+change.position},onDragEnd={finish()},onDragCancel={dragPoint=null})}
                    .padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DragIndicator,null,tint=Color.White)
                    Column(Modifier.weight(1f).padding(start=8.dp)) {
                        Label("추가할 일정",17,color=Color.White.copy(.85f),maxLines=1)
                        Label(candidate!!.name,24,FontWeight.Bold,Color.White,maxLines=1)
                    }
                }
                Label("원하는 일정 사이로 끌어 놓으세요.",18,color=Muted)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    SelectChip("맨 앞",false,Modifier.weight(1f).testTag("insert-first")) {insertion=0}
                    SelectChip("도착 전",false,Modifier.weight(1f).testTag("insert-last")) {insertion=stops.size}
                }
            }
            for(i in 0..stops.size) {
                val active=dragPoint?.let {point->zones.minByOrNull {(_,r)->kotlin.math.abs(r.center.y-point.y)}?.key==i}?:false
                Box(Modifier.fillMaxWidth().height(if(dragPoint!=null) 38.dp else if(insertion==null) 16.dp else 3.dp).testTag("realtime-drop-$i")
                    .onGloballyPositioned {zones[i]=it.boundsInRoot()}.background(if(active) Purple.copy(.25f) else Color.Transparent,RoundedCornerShape(10.dp)))
                if(insertion==i&&candidate!=null&&scope==null) Row(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(22.dp)).background(Purple).padding(horizontal=22.dp),verticalAlignment=Alignment.CenterVertically) {
                    Label("추가할 일정",18,FontWeight.Bold,Color.White.copy(.9f),maxLines=1)
                    Spacer(Modifier.width(12.dp))
                    Label(candidate!!.name,24,FontWeight.Bold,Color.White,Modifier.weight(1f),maxLines=1)
                }
                if(i<stops.size) ScheduleRow("stop ${i+1}",stops[i].name,"+${JourneyEngine.legMinutes(state,stops,i)}분")
            }
            ScheduleRow("도착",state.destination,JourneyEngine.clock(state.departureMinute+state.members.first {it.id==state.viewerId}.baseMinutes+
                state.changes.filter {it.ownerId==state.viewerId||it.scope==RouteScope.GROUP}.sumOf {it.addedMinutes}+if(insertion!=null&&scope==null) 12 else 0))
        }
        if(insertion!=null) {
            Label("일정 추가 · 선택하면 바로 경로에 반영돼요",23,FontWeight.Bold,maxLines=1)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ScheduleChoice("내 차만",scope==RouteScope.PERSONAL,Modifier.weight(1f).testTag("scope-personal")) {applyScope(RouteScope.PERSONAL)}
                ScheduleChoice("모두 경로에 반영",scope==RouteScope.GROUP,Modifier.weight(1f).testTag("scope-group")) {applyScope(RouteScope.GROUP)}
            }
            Label("친구들에게 알릴까요?",23,FontWeight.Bold,modifier=Modifier.padding(top=4.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ScheduleChoice("응, 음성 메시지로",false,Modifier.weight(1f).testTag("notify-voice"),scope!=null) {
                    val sample=state.story?.messages?.get(if(candidate?.name==state.story?.cafe?.name) "cafe" else "rest")?:"sorry I have to go to the bathroom"
                    request(InputRequest("Gleo AI · 친구들에게 전할 음성 메시지","",sample,true) {finish(it,true)})
                }
                ScheduleChoice("아니",false,Modifier.weight(1f).testTag("notify-no"),scope!=null) {finish("",false)}
            }
        }
        ScheduleChoice("뒤로가기",false,Modifier.fillMaxWidth().testTag("waypoint-back")) {
            if(scope!=null) back()
            else if(insertion!=null) {insertion=null;scope=null} else back()
        }
    }
}

@Composable private fun ScheduleChoice(text:String,selected:Boolean,modifier:Modifier,enabled:Boolean=true,click:()->Unit) {
    Surface(onClick=click,enabled=enabled,shape=RoundedCornerShape(18.dp),color=if(selected) Purple else Color.White,
        shadowElevation=if(enabled) 1.dp else 0.dp,modifier=modifier.height(56.dp)) {
        Box(Modifier.padding(horizontal=10.dp),contentAlignment=Alignment.Center) {
            Text(text,color=if(!enabled) Color(0xFFB2B4BC) else if(selected) Color.White else Ink,fontFamily=TravelReviewFont,
                fontWeight=FontWeight.Bold,fontSize=20.sp,lineHeight=24.sp,maxLines=1,
                overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable internal fun NotificationsPanel(store: TripStore, services: ConnectServices, request: (InputRequest) -> Unit) {
    val state = store.state
    var playing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playing) { if (playing != null) { delay(4500); playing = null } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Label("${JourneyEngine.viewer(state).name}의 알림", 23, color = Muted)
        if (JourneyEngine.inbox(state).isEmpty()) {
            Label("함께하는 여행이 시작됐어요", 28, FontWeight.Bold)
            Label("${state.groupName}의 멤버 ${state.members.size}명이 연결되었어요.", 24, color = Muted)
        }
        JourneyEngine.inbox(state).forEach { note ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Paper).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Label(note.title, 26, FontWeight.Bold); Label(note.text, 22, color = Muted)
                if (note.voice.isNotBlank()) {
                    Label("“${note.voice}”", 24, color = Purple)
                    Action(if (playing == note.id) "음성 메시지 재생 중…" else "음성 메시지 듣기", {
                        playing = note.id; services.announce(note.voice)
                        store.update { it.copy(notes = it.notes.map { n -> if (n.id == note.id) n.copy(readBy = (n.readBy + it.viewerId).distinct()) else n }) }
                    }, Modifier.fillMaxWidth().testTag("listen-${note.id}"), secondary = true)
                }
                if (note.from != state.viewerId) Action("음성으로 답장", {
                    val sample = state.story?.messages?.get(if (state.viewerId == "a") "dohyunReply" else "seoyunReply") ?: "좋아! 거기서 만나자."
                    request(InputRequest("Gleo AI · 음성으로 답장", "", sample, true) { value -> store.update { JourneyEngine.reply(it,value) }; services.announce("음성 답장을 보냈어요") })
                }, Modifier.fillMaxWidth().testTag("reply-voice"))
            }
        }
    }
}

@Composable internal fun LadderPanel(store: TripStore, services: ConnectServices, request: (InputRequest) -> Unit) {
    val state = store.state; val game = state.game
    var drawing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    fun update(value: LadderState) = store.update { it.copy(game = value) }
    LaunchedEffect(drawing) {
        if (drawing) {
            for (i in 1..40) { delay(35); progress = i / 40f }
            val winner = game.winner.ifBlank { game.participants.random() }
            store.update { it.copy(game = it.game.copy(winner = winner, finished = true), notes = it.notes + TravelNote(from = it.viewerId,
                recipients = game.participants, title = "사다리 결과 · ${game.reason}", text = "${it.members.first { m -> m.id == winner }.name} 당첨!")) }
            drawing = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Label("오늘도 함께 웃을 이유", 26, FontWeight.Bold)
        OutlinedTextField(game.reason, { update(game.copy(reason = it, finished = false)) }, Modifier.fillMaxWidth().testTag("game-reason"),
            placeholder = { Label("게임하는 이유 적기", 23, color = Muted) }, enabled = !drawing, textStyle = textStyle(), shape = RoundedCornerShape(20.dp))
        GleoButton(services.listening) { if (!drawing) request(InputRequest("Gleo AI · 게임하는 이유", game.reason, state.story?.messages?.get("gameReason") ?: "오늘 점심 쏘기",true) { update(game.copy(reason = it, finished = false)) }) }
        Label("함께할 친구를 선택해 주세요", 24, FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.members.forEach { member -> SelectChip(member.name, member.id in game.participants, Modifier.weight(1f).testTag("player-${member.id}")) {
                if (!drawing) update(game.copy(participants = if (member.id in game.participants) game.participants - member.id else game.participants + member.id, finished = false))
            } }
        }
        Label("사다리 게임", 28, FontWeight.Bold, Purple)
        Canvas(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(25.dp)).background(Paper)) {
            val players = game.participants.size.coerceAtLeast(2)
            val gap = size.width / (players + 1)
            for (i in 1..players) drawLine(Color(0xFFBDC0CC), Offset(gap*i, 25f),Offset(gap*i, size.height - 25),7f)
            for (row in 0..5) {
                val col = row % (players - 1); val y = size.height * (row+1) / 7
                drawLine(Color(0xFFBDC0CC),Offset(gap*(col+1),y),Offset(gap*(col+2),y),7f)
            }
            if (drawing || game.finished) {
                val finishColumn = game.participants.indexOf(game.winner).coerceAtLeast(0)
                fun landing(start:Int):Int {
                    var column=start
                    for(row in 0..5) { val left=row%(players-1); column=when(column) { left -> left+1; left+1 -> left; else -> column } }
                    return column
                }
                var column=(0 until players).first { landing(it)==finishColumn }
                val path=androidx.compose.ui.graphics.Path().apply {
                    moveTo(gap*(column+1),25f)
                    for(row in 0..5) {
                        val y=size.height*(row+1)/7;val left=row%(players-1)
                        lineTo(gap*(column+1),y)
                        column=when(column) { left -> left+1; left+1 -> left; else -> column }
                        lineTo(gap*(column+1),y)
                    }
                    lineTo(gap*(column+1),size.height-25)
                }
                val measure=androidx.compose.ui.graphics.PathMeasure().apply { setPath(path,false) }
                val trace=androidx.compose.ui.graphics.Path()
                measure.getSegment(0f,measure.length*if(game.finished) 1f else progress,trace,true)
                drawPath(trace,Purple,style=androidx.compose.ui.graphics.drawscope.Stroke(10f,cap=androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        if (game.finished) {
            state.members.filter { it.id in game.participants }.forEach { m ->
                Label("${m.name}   ${if (m.id == game.winner) "당첨! 🎉" else "패스"}", 29, FontWeight.Bold, if (m.id == game.winner) Purple else Muted)
            }
            state.story?.let { Label("“${it.messages["gameJoke"]}”\n“${it.messages["gameReply"]}”", 22, color = Muted) }
        }
        Action(if (drawing) "사다리 타는 중…" else if (game.finished) "한 번 더 하기" else "사다리 시작", {
            val winner=state.story?.messages?.get("gameWinner")?.takeIf { it in game.participants } ?: game.participants.random()
            update(game.copy(winner=winner,finished=false)); drawing = true; progress = 0f
        },
            Modifier.fillMaxWidth().testTag("game-start"), !drawing && game.reason.isNotBlank() && game.participants.size >= 2 && services.policyKnown && !services.restricted)
        Label("주차 중에만 즐길 수 있어요. 게임 결과는 일정과 경로를 바꾸지 않아요.", 18, color = Muted)
    }
}
