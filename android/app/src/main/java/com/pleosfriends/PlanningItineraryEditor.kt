package com.pleosfriends

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import kotlin.math.abs
import kotlin.math.roundToInt

/** Local draft: dropping and reordering never alter the saved day until '일정 저장'. */
@Composable internal fun PlanningItineraryEditor(day:Int,state:TravelState,services:ConnectServices,
    close:()->Unit,save:(List<Place>)->Unit) {
    var stops by remember(day) { mutableStateOf(state.days[day].stops) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    var floatingName by remember { mutableStateOf("") }
    var canvasBounds by remember { mutableStateOf(Rect.Zero) }
    var routeBounds by remember { mutableStateOf(Rect.Zero) }
    val zones=remember { mutableStateMapOf<Int,Rect>() }
    val haptic=LocalHapticFeedback.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val origin=if(day==0) state.members.first().origin else JourneyEngine.resolve(state,state.destination.ifBlank { "이전 날 숙소" })
    val destination=if(day==state.days.lastIndex && state.days.size>1) state.members.first().origin else JourneyEngine.resolve(state,state.destination.ifBlank { "최종 도착지" })
    val activeSlot=dragPoint?.takeIf { routeBounds.inflate(20f).contains(it) }?.let { point ->
        zones.filterKeys { it in 0..stops.size }.minByOrNull { abs(it.value.center.y-point.y) }?.key
    }
    fun insert(index:Int) {
        if(query.isBlank()) return
        if(stops.size>=3) { message="하루에 경유지 3개까지 추가할 수 있어요.";return }
        focus.clearFocus();keyboard?.hide()
        val place=JourneyEngine.resolve(state,query)
        stops=TripRules.insert(stops,place,index)
        query="";message="원하는 순서로 추가했어요. 저장하면 일정에 반영돼요."
    }
    fun finishDrag() {
        val target=dragPoint?.takeIf { routeBounds.inflate(20f).contains(it) }?.let { point ->
            zones.filterKeys { it in 0..stops.size }.minByOrNull { abs(it.value.center.y-point.y) }?.key
        }
        if(target!=null) {
            if(dragIndex==-1) insert(target)
            else dragIndex?.let { stops=TripRules.move(stops,it,target);message="방문 순서를 바꿨어요. 저장하면 일정에 반영돼요." }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        dragPoint=null;dragIndex=null
    }
    fun begin(index:Int,name:String,bounds:Rect,point:Offset) {
        focus.clearFocus();keyboard?.hide()
        dragIndex=index;floatingName=name;dragPoint=bounds.topLeft+point
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    BackHandler { services.stopListening();close() }
    Box(Modifier.fillMaxSize().background(Ink.copy(.24f)).pointerInput(Unit) { detectTapGestures { } }
        .onGloballyPositioned { canvasBounds=it.boundsInRoot() },contentAlignment=Alignment.Center) {
        Column(Modifier.widthIn(max=1040.dp).fillMaxWidth(.92f).heightIn(max=1020.dp)
            .shadow(28.dp,RoundedCornerShape(40.dp)).clip(RoundedCornerShape(40.dp)).background(Color.White).padding(32.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Label("${day+1}일차 일정",34,androidx.compose.ui.text.font.FontWeight.Bold)
                    Label("주소 검색 또는 Gleo AI로 장소를 넣은 뒤, 출발과 도착 사이로 끌어 놓으세요.",21,color=Muted,modifier=Modifier.padding(top=7.dp),maxLines=2)
                }
                IconButton(close,Modifier.size(52.dp).testTag("close-itinerary-editor")) { Icon(Icons.Rounded.Close,"닫기",Modifier.size(28.dp)) }
            }
            Spacer(Modifier.height(22.dp))
            Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(query,{query=it.take(60)},Modifier.weight(1f).testTag("stop-input"),
                        placeholder={Label("주소 또는 장소 검색",24,color=Muted)},singleLine=true,textStyle=textStyle().copy(fontSize=26.sp),
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}),
                        leadingIcon={Icon(Icons.Rounded.Search,null,tint=Muted)},shape=RoundedCornerShape(18.dp),
                        colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Line,focusedBorderColor=Purple))
                    GleoButton(services.listening) { focus.clearFocus();keyboard?.hide();services.requestVoice(state.story?.lunch?.name ?: "한강",{query=it},{message=it}) }
                }
                if(query.isNotBlank()) {
                    var bounds by remember { mutableStateOf(Rect.Zero) }
                    PlanningCandidate(query,Modifier.fillMaxWidth().testTag("candidate-stop").onGloballyPositioned { bounds=it.boundsInRoot() }
                        .alpha(if(dragIndex==-1) .28f else 1f).pointerInput(query) { detectDragGestures(
                            onDragStart={begin(-1,query,bounds,it)},onDrag={change,_->change.consume();dragPoint=bounds.topLeft+change.position},
                            onDragEnd={finishDrag()},onDragCancel={dragPoint=null;dragIndex=null}) },{insert(stops.size)})
                } else {
                    Row(Modifier.fillMaxWidth().height(72.dp).background(Paper,RoundedCornerShape(18.dp)).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TouchApp,null,Modifier.size(24.dp),tint=Purple);Spacer(Modifier.width(12.dp))
                        Label("장소를 입력하면, 끌어 놓을 수 있는 일정이 만들어져요.",21,color=Muted)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Label("현재 일정",24,androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.weight(1f));Label("경유지 ${stops.size} / 3",19,color=Muted)
                }
                Column(Modifier.fillMaxWidth().animateContentSize().onGloballyPositioned { routeBounds=it.boundsInRoot() }) {
                    PlanningRouteRow("출발",origin,null,Modifier.testTag("planning-origin"))
                    for(i in 0..stops.size) {
                        val fill by animateColorAsState(if(activeSlot==i) Purple.copy(.12f) else Color.Transparent,label="insertion-slot")
                        Box(Modifier.fillMaxWidth().height(28.dp).testTag("drop-zone-$i").onGloballyPositioned { zones[i]=it.boundsInRoot() }
                            .background(fill,RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center) {
                            if(activeSlot==i) Box(Modifier.fillMaxWidth(.94f).height(3.dp).background(Purple,RoundedCornerShape(2.dp)))
                        }
                        if(i<stops.size) {
                            var bounds by remember { mutableStateOf(Rect.Zero) }
                            PlanningRouteRow("stop ${i+1}",stops[i],{stops=stops.toMutableList().apply { removeAt(i) }},
                                Modifier.testTag("planned-stop-$i").onGloballyPositioned { bounds=it.boundsInRoot() }.alpha(if(dragIndex==i) .28f else 1f)
                                    .pointerInput(stops,i) { detectDragGestures(onDragStart={begin(i,stops[i].name,bounds,it)},
                                        onDrag={change,_->change.consume();dragPoint=bounds.topLeft+change.position},
                                        onDragEnd={finishDrag()},onDragCancel={dragPoint=null;dragIndex=null}) })
                        }
                    }
                    PlanningRouteRow("도착",destination,null,Modifier.testTag("planning-destination"))
                }
            }
            Spacer(Modifier.height(14.dp))
            Label(message.ifBlank { if (state.story != null) "첫날은 점심과 호텔만 넣어요. 카페는 플픽에서 나중에." else "장소를 검색하거나 Gleo AI로 말한 뒤 끌어 놓으세요." },20,color=if(message.isBlank()) Muted else Purple,maxLines=1)
            Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Action("닫기",{services.stopListening();close()},Modifier.weight(.42f),secondary=true)
                Action("일정 저장",{services.stopListening();save(stops)},Modifier.weight(1f).testTag("save-stops"))
            }
        }
        dragPoint?.let { point ->
            PlanningCandidate(floatingName,Modifier.offset { IntOffset((point.x-canvasBounds.left-270.dp.toPx()).roundToInt(),(point.y-canvasBounds.top-36.dp.toPx()).roundToInt()) }
                .align(Alignment.TopStart).width(540.dp).shadow(18.dp,RoundedCornerShape(18.dp)).clearAndSetSemantics { },null)
        }
    }
}

@Composable private fun PlanningCandidate(name:String,modifier:Modifier,append:(()->Unit)?) {
    Row(modifier.height(76.dp).clip(RoundedCornerShape(18.dp)).background(Purple).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(Icons.Rounded.DragIndicator,null,Modifier.size(28.dp),tint=Color.White.copy(.7f));Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Label("추가할 일정",17,color=Color.White.copy(.75f),maxLines=1);Label(name,25,androidx.compose.ui.text.font.FontWeight.Bold,Color.White,maxLines=1) }
        if(append!=null) TextButton(append,Modifier.testTag("add-stop")) { Label("추가",21,androidx.compose.ui.text.font.FontWeight.Bold,Color.White,maxLines=1) }
    }
}

@Composable private fun PlanningRouteRow(kind:String,place:Place,remove:(()->Unit)?,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(18.dp)).background(Paper).padding(start=18.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.width(70.dp),contentAlignment=Alignment.CenterStart) { Label(kind,19,androidx.compose.ui.text.font.FontWeight.Bold,if(remove==null) Ink else Purple) }
        Column(Modifier.weight(1f).padding(end=8.dp)) {
            Label(place.name,25,androidx.compose.ui.text.font.FontWeight.Bold,maxLines=1)
            if(place.address.isNotBlank()) Label(place.address,18,color=Muted,maxLines=1)
        }
        if(remove!=null) {
            Icon(Icons.Rounded.DragIndicator,"${place.name} 순서 변경",Modifier.size(25.dp),tint=Muted)
            IconButton(remove,Modifier.size(48.dp).testTag("remove-planned-${kind.substringAfter(" ")}")) { Icon(Icons.Rounded.Close,"${place.name} 삭제",Modifier.size(24.dp),tint=Muted) }
        } else Icon(Icons.Rounded.Lock,null,Modifier.padding(horizontal=14.dp).size(20.dp),tint=Muted.copy(.5f))
    }
}
