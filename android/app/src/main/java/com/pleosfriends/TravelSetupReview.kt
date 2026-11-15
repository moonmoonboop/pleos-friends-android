package com.pleosfriends

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Approved setup-screen layout and the heavier font family shared by the demo. */
internal val LocalTravelSetupReview = staticCompositionLocalOf { false }
internal val TravelReviewCanvas = Color(0xFFF7F8FA)
internal val TravelReviewFont = FontFamily(
    Font(R.font.asta_review_semibold, FontWeight.SemiBold),
    Font(R.font.asta_review_bold, FontWeight.Bold),
    Font(R.font.asta_review_extrabold, FontWeight.ExtraBold),
)
private val ControlFill = Color.White
private val TimelineFill = Color(0xFFEDEEF2)
private val Track = Color(0xFF7F828A)

@Composable internal fun ReviewTripSetup(state: TravelState, back: () -> Unit,
    destination: () -> Unit, duration: () -> Unit, edit: (Int) -> Unit, next: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=46.dp,vertical=36.dp)) {
        Row(Modifier.fillMaxWidth().height(86.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            ReviewBack(back)
            CompactTripField("내 출발지 (집)",state.members.first().origin.name,Modifier.weight(1f))
            CompactTripField("최종 여행지",state.destination,Modifier.weight(1.05f).testTag("destination"),destination)
            Spacer(Modifier.width(20.dp))
            Surface(onClick=duration,color=ControlFill,shape=RoundedCornerShape(22.dp),modifier=Modifier.shadow(2.dp,RoundedCornerShape(22.dp)).width(116.dp).height(76.dp).testTag("duration")) {
                Box(contentAlignment=Alignment.Center) { ReviewText("${state.nights}박",30,FontWeight.Bold) }
            }
            Surface(onClick=duration,color=ControlFill,shape=RoundedCornerShape(22.dp),modifier=Modifier.shadow(2.dp,RoundedCornerShape(22.dp)).width(116.dp).height(76.dp).testTag("duration-days")) {
                Box(contentAlignment=Alignment.Center) { ReviewText("${state.days.size}일",30,FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(30.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            state.days.forEachIndexed { index,day ->
                Column(Modifier.fillMaxWidth().testTag("timeline-day-$index")) {
                    Row(Modifier.fillMaxWidth().height(46.dp),verticalAlignment=Alignment.CenterVertically) {
                        ReviewText("${index+1}일차",29,FontWeight.Bold)
                        if(index==state.days.lastIndex && state.days.size>1) ReviewText(" (마지막날)",29,FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton({ edit(index) },Modifier.height(46.dp).testTag("edit-day-$index"),contentPadding=PaddingValues(horizontal=10.dp)) {
                            ReviewText("일정 추가",27,FontWeight.Bold,Purple)
                        }
                    }
                    HorizontalDayTimeline(index,state,day)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        ReviewText("(!) 여행이 구체적이지 않아도 괜찮아요 :) 플레오스가 도울게요",25,FontWeight.SemiBold,
            modifier=Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
        ReviewStartButton(next,TripRules.mayStart(state),Modifier.width(720.dp).align(Alignment.CenterHorizontally).testTag("setup-next"))
    }
}

@Composable private fun HorizontalDayTimeline(index:Int,state:TravelState,day:DayPlan) {
    val last=index==state.days.lastIndex && state.days.size>1
    val start=if(index==0) "출발" else state.destination.ifBlank { "숙소" }
    val end=if(last) "내 집" else state.destination.ifBlank { "도착" }
    val labels=listOf(start)+day.stops.map { it.name }+end
    BoxWithConstraints(Modifier.fillMaxWidth().height(140.dp).background(TimelineFill,RoundedCornerShape(20.dp))) {
        val edge=110.dp
        val step=(maxWidth-edge*2)/(labels.size-1)
        Canvas(Modifier.fillMaxWidth().height(66.dp)) {
            val y=40.dp.toPx();val x=edge.toPx();val spacing=(size.width-2*x)/(labels.size-1)
            drawLine(Track.copy(.55f),Offset(x,y),Offset(size.width-x,y),2.dp.toPx())
            labels.indices.forEach { i ->
                val center=Offset(x+spacing*i,y)
                drawCircle(if(i==0 || i==labels.lastIndex) Ink else Purple,if(i==0 || i==labels.lastIndex) 8.dp.toPx() else 7.dp.toPx(),center)
                if(i>0 && i<labels.lastIndex) drawCircle(Color.White,2.5.dp.toPx(),center)
            }
        }
        labels.forEachIndexed { i,label ->
            Box(Modifier.offset(x=edge+step*i-118.dp,y=66.dp).width(236.dp),contentAlignment=Alignment.TopCenter) {
                Text(label,color=if(i>0 && i<labels.lastIndex) Purple else Ink,fontFamily=TravelReviewFont,fontWeight=FontWeight.Bold,fontSize=22.sp,
                    lineHeight=26.sp,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable internal fun ReviewFriends(state:TravelState,back:()->Unit,group:()->Unit,invite:()->Unit,next:()->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=46.dp,vertical=36.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
            ReviewBack(back)
            Spacer(Modifier.weight(1f))
            Surface(onClick=group,color=ControlFill,shape=RoundedCornerShape(22.dp),modifier=Modifier.shadow(2.dp,RoundedCornerShape(22.dp)).width(720.dp).height(88.dp).testTag("group-name")) {
                Box(Modifier.fillMaxSize().padding(horizontal=38.dp),contentAlignment=Alignment.Center) {
                    ReviewText(state.groupName.ifBlank { "그룹 이름 정하기" },32,FontWeight.Bold,modifier=Modifier.padding(end=32.dp))
                    Icon(Icons.Rounded.AutoAwesome,"Gleo AI 또는 직접 입력",Modifier.align(Alignment.CenterEnd).size(28.dp),tint=Purple)
                }
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(54.dp))
        }
        Spacer(Modifier.height(108.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
            ReviewText("초대한 친구",29,FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            ReviewText("${state.joined+1} / ${state.members.size}",24,FontWeight.SemiBold,Muted)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().height(292.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            state.members.drop(1).take(state.invited).forEachIndexed { index,member ->
                Column(Modifier.weight(1f).fillMaxHeight().shadow(2.dp,RoundedCornerShape(24.dp)).background(ControlFill,RoundedCornerShape(24.dp)).padding(horizontal=18.dp,vertical=30.dp),
                    horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.size(66.dp).background(CarColors[index+1].copy(.12f),RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center) { Icon(Icons.Rounded.DirectionsCar,null,Modifier.size(32.dp),tint=CarColors[index+1]) }
                    Spacer(Modifier.height(18.dp))
                    ReviewText(member.name,30,FontWeight.Bold)
                    ReviewText(member.origin.name,20,FontWeight.SemiBold,Muted,modifier=Modifier.padding(top=8.dp))
                    Spacer(Modifier.weight(1f))
                    ReviewText(if(index<state.joined) "도착까지 약 ${member.baseMinutes}분" else "참여 기다리는 중",22,FontWeight.SemiBold,
                        if(index<state.joined) Ink else Muted)
                }
            }
            val mayInvite=state.invited<state.members.size-1
            Surface(onClick=invite,enabled=mayInvite,color=Color(0xFFEDEEF2),shape=RoundedCornerShape(20.dp),
                modifier=Modifier.weight(1f).fillMaxHeight().testTag("invite-tile")) {
                Box(contentAlignment=Alignment.Center) {
                    Icon(Icons.Rounded.Add,if(mayInvite) "친구 초대" else "초대 완료",Modifier.size(62.dp),tint=if(mayInvite) Ink else Color(0xFFADADB4))
                }
            }
            // Keep the '+' the same size while the invited-friend row fills up.
            repeat((state.members.size-1-state.invited).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.weight(1f))
        ReviewStartButton(next,TripRules.mayConfirm(state),Modifier.width(720.dp).align(Alignment.CenterHorizontally).testTag("friends-next"))
        Spacer(Modifier.height(104.dp))
    }
}

@Composable private fun CompactTripField(label:String,value:String,modifier:Modifier,click:(()->Unit)?=null) {
    val base=modifier.shadow(2.dp,RoundedCornerShape(22.dp)).height(80.dp)
    Surface(onClick={click?.invoke()},enabled=click!=null,color=ControlFill,shape=RoundedCornerShape(22.dp),modifier=base) {
        Row(Modifier.fillMaxSize().padding(horizontal=22.dp),verticalAlignment=Alignment.CenterVertically) {
            if(value.isBlank()) ReviewText(label,28,FontWeight.Bold,modifier=Modifier.weight(1f))
            else {
                ReviewText(label,19,FontWeight.SemiBold,Muted)
                Spacer(Modifier.width(12.dp))
                ReviewText(value,27,FontWeight.Bold,modifier=Modifier.weight(1f),maxLines=1)
            }
            if(click!=null) Icon(Icons.Rounded.AutoAwesome,null,Modifier.padding(start=8.dp).size(24.dp),tint=Purple)
        }
    }
}
@Composable private fun ReviewBack(back:()->Unit) {
    IconButton(back,Modifier.size(54.dp).testTag("back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack,"뒤로",Modifier.size(28.dp),tint=Ink) }
}
@Composable private fun ReviewStartButton(click:()->Unit,enabled:Boolean,modifier:Modifier) {
    Button(click,modifier.height(90.dp),enabled=enabled,shape=RoundedCornerShape(23.dp),
        colors=ButtonDefaults.buttonColors(containerColor=Purple,disabledContainerColor=ControlFill,disabledContentColor=Color(0xFF92929A))) {
        ReviewText("여행 시작",34,FontWeight.ExtraBold,if(enabled) Color.White else Color(0xFF92929A))
    }
}
@Composable private fun ReviewText(text:String,size:Int,weight:FontWeight=FontWeight.SemiBold,color:Color=Ink,modifier:Modifier=Modifier,maxLines:Int=Int.MAX_VALUE) {
    Text(text,modifier,color,fontSize=size.sp,fontFamily=TravelReviewFont,fontWeight=weight,lineHeight=(size*1.3f).sp,maxLines=maxLines,overflow=TextOverflow.Ellipsis)
}
