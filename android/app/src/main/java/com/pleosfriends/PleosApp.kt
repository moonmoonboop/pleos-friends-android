package com.pleosfriends

import android.Manifest
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

internal val Purple = Color(0xFF5A46FA)
internal val Ink = Color(0xFF131417)
internal val Muted = Color(0xFF686A72)
internal val Paper = Color(0xFFF7F8FA)
internal val Line = Color(0xFFE0E1E6)
internal val CarColors = listOf(Color(0xFFF0C000), Color(0xFFE53935), Color(0xFF3B82F6), Color(0xFF7C5CFF))
private val Asta = TravelReviewFont

@Composable fun PleosTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(colorScheme = lightColorScheme(primary = Purple, onPrimary = Color.White,
        background = Paper, surface = Color.White, onSurface = Ink, onBackground = Ink),
        typography = Typography(bodyLarge = base.bodyLarge.copy(fontFamily = Asta, fontWeight = FontWeight.SemiBold),
            bodyMedium = base.bodyMedium.copy(fontFamily = Asta, fontWeight = FontWeight.SemiBold),
            labelLarge = base.labelLarge.copy(fontFamily = Asta, fontWeight = FontWeight.Bold)), content = content)
}

internal data class InputRequest(val title: String, val value: String, val sample: String, val autoListen: Boolean = false, val save: (String) -> Unit)

@Composable fun PleosApp(store: TripStore, services: ConnectServices) {
    val state = store.state
    var input by remember { mutableStateOf<InputRequest?>(null) }
    var editDay by remember { mutableStateOf<Int?>(null) }
    var invite by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) services.startLocation()
    }
    fun start() {
        store.update { TripRules.start(it) }
        services.startNavigation(store.state)
        if (!BuildConfig.DEMO_MODE) permission.launch("pleos.car.permission.FUSED_LOCATION")
        services.announce("여행을 시작합니다. 따로 타도 함께 가요.")
    }
    LaunchedEffect(state.started, state.screen) {
        if (BuildConfig.DEMO_MODE && state.started && state.screen == Screen.HUB) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { delay(1000); store.update(persist = false) { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                    if (store.state.elapsedSeconds % 10 == 0) store.save() }
            }
        }
    }
    // Finish interrupted local invitations when this screen is restored, without sending an SMS.
    LaunchedEffect(state.invited, state.joined) {
        if (state.joined < state.invited) { delay(650); store.update { it.copy(joined = (it.joined + 1).coerceAtMost(it.invited)) } }
    }
    LaunchedEffect(services.restricted) {
        if (services.restricted) { input = null; editDay = null; invite = false; services.stopListening() }
    }
    fun back() {
        when {
            input != null -> { services.stopListening(); input = null }
            editDay != null -> editDay = null
            invite -> invite = false
            info != null -> info = null
            state.panel != Panel.NONE -> store.update { it.copy(panel = Panel.NONE) }
            state.screen == Screen.HOME -> Unit
            state.screen == Screen.SETUP || state.screen == Screen.HUB || state.screen == Screen.PICKS || state.screen == Screen.HISTORY -> store.go(Screen.HOME)
            state.screen == Screen.FRIENDS -> store.go(Screen.SETUP)
            state.screen == Screen.CONFIRM -> store.go(Screen.FRIENDS)
            state.screen == Screen.SUMMARY -> store.go(Screen.CONFIRM)
        }
    }
    BackHandler(state.screen != Screen.HOME || input != null) { back() }
    val reviewing = state.screen == Screen.SETUP || state.screen == Screen.FRIENDS
    BoxWithConstraints(Modifier.fillMaxSize().background(Paper)) {
        val original = LocalDensity.current
        val scale = minOf(maxWidth.value / 1450f, maxHeight.value / 1080f).coerceIn(.5f, 1.4f)
        CompositionLocalProvider(LocalDensity provides Density(original.density * scale, 1f), LocalTravelSetupReview provides reviewing) {
            val unsafe = state.screen in listOf(Screen.SETUP, Screen.FRIENDS, Screen.CONFIRM, Screen.SUMMARY, Screen.PICKS, Screen.HISTORY)
            Column(Modifier.fillMaxSize()) {
            if (BuildConfig.DEMO_MODE) DemoViewerBar(state) { id ->
                store.update {
                    it.copy(viewerId = id, panel = if (it.started) Panel.NOTIFICATIONS else it.panel)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = if (BuildConfig.DEMO_MODE) 16.dp else 28.dp,
                top = if (BuildConfig.DEMO_MODE) 12.dp else 28.dp, end = if (BuildConfig.DEMO_MODE) 16.dp else 28.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(36.dp)).background(if (reviewing) TravelReviewCanvas else Paper)) {
                if (unsafe && (services.restricted || !services.policyKnown)) {
                    Column(Modifier.align(Alignment.Center).padding(64.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(26.dp)) {
                        Icon(Icons.Rounded.DirectionsCar, null, Modifier.size(70.dp), tint = Purple)
                        Label("주차 후 설정해 주세요", 40, FontWeight.Bold)
                        Label(if (services.policyKnown) "주행 중에는 여행 설정을 잠시 쉬어갑니다." else "차량의 주차 상태를 확인하고 있어요.", 25, color = Muted)
                        Action("홈으로", { store.go(Screen.HOME) })
                    }
                } else when (state.screen) {
                    Screen.HOME -> Home(state.started, { if (state.started) store.go(Screen.HUB) else store.go(Screen.SETUP) },
                        { store.go(Screen.PICKS) }, { store.go(Screen.HISTORY) })
                    Screen.SETUP -> Setup(state, { back() }, { nights -> store.update { TripRules.resize(it, nights) } },
                        { input = InputRequest("최종 여행지", state.destination, state.story?.destination?.name ?: "성수 연무장") { value -> store.update { it.copy(destination = value) } } },
                        { editDay = it }, { store.go(Screen.FRIENDS) })
                    Screen.FRIENDS -> Friends(state, { back() },
                        { input = InputRequest("그룹 이름 정하기", state.groupName, state.story?.groupName ?: "여자들우정영원히") { value -> store.update { it.copy(groupName = value) } } },
                        { invite = true }, { store.go(Screen.CONFIRM) })
                    Screen.CONFIRM -> Confirmation(state, { back() }, { store.go(Screen.SUMMARY) })
                    Screen.SUMMARY -> Summary(state, { back() }, { start() })
                    Screen.HUB -> Hub(store, services, input?.title?.contains("음성") == true, { if (services.policyKnown && !services.restricted) input = it else info = "주차 후 입력해 주세요." }, { store.go(Screen.HOME) }, { info = it })
                    Screen.PICKS -> StandalonePicks(store, { back() })
                    Screen.HISTORY -> HistoryScreen(state, { back() }, { store.go(Screen.HUB) })
                }
                input?.let { request -> InputOverlay(request, services, state, { input = null; services.stopListening() }) { value -> request.save(value); input = null } }
                editDay?.let { day -> ItineraryOverlay(day, state, services, { services.stopListening(); editDay = null }) { stops ->
                    store.update { current -> current.copy(days = current.days.mapIndexed { index, plan -> if (index == day) DayPlan(stops) else plan }) }; editDay = null
                } }
                if (invite) InviteOverlay(state, { invite = false }) { store.update { it.copy(invited = if (it.story != null) (it.invited + 1).coerceAtMost(it.members.size - 1) else it.members.size - 1) }; invite = false }
                info?.let { message ->
                    if (message.startsWith("✓")) CompletionPresentation(message) { info = null }
                    else Overlay("Pleos Friends", { info = null }) {
                        Label(message, 27, color = Muted); Spacer(Modifier.height(35.dp)); Action("확인", { info = null }, Modifier.fillMaxWidth().testTag("completion-confirm"))
                    }
                }
            }
            ConnectLightDock()
            }
        }
    }
}

@Composable internal fun Label(text: String, size: Int = 28, weight: FontWeight = FontWeight.Normal,
    color: Color = Ink, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color, size.sp, fontFamily = Asta, fontWeight = if(weight < FontWeight.SemiBold) FontWeight.SemiBold else weight,
        lineHeight = (size * 1.4f).sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
@Composable internal fun Action(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, secondary: Boolean = false) {
    Button(onClick, modifier.height(64.dp), enabled = enabled, shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (secondary) Color(0xFFEAE7FF) else Purple,
            contentColor = if (secondary) Purple else Color.White, disabledContainerColor = Color(0xFFE0E1E7), disabledContentColor = Color(0xFF9B9EA8)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
        Label(text, 24, FontWeight.Bold, if (enabled) if (secondary) Purple else Color.White else Color(0xFF9B9EA8), maxLines = 1)
    }
}
@Composable internal fun Header(title: String, subtitle: String = "", back: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(back, Modifier.size(80.dp).background(Color.White, CircleShape).testTag("back")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", Modifier.size(32.dp))
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) { Label(title, 40, FontWeight.Bold); if (subtitle.isNotEmpty()) Label(subtitle, 22, color = Muted) }
        trailing()
    }
}
@Composable private fun Home(resume: Boolean, journey: () -> Unit, picks: () -> Unit, history: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.padding(start = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            Label("Pleos Friends", 42, FontWeight.Bold)
            Spacer(Modifier.width(23.dp)); Label("따로 타도, 함께 가는 여행", 25, color = Muted)
        }
        HomeTile("여행 시작하기", if (resume) "진행 중인 우리의 여행으로 돌아가요" else "우리의 다음 이야기는 어디로 갈까요?", R.drawable.home_journey,
            Icons.Rounded.NorthEast, Modifier.fillMaxWidth().weight(1.1f).testTag("home-trip"), journey, selected = true)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            HomeTile("Pleos Picks 둘러보기", "함께 가고 싶은 곳을 발견해요", R.drawable.home_picks,
                Icons.Rounded.AutoAwesome, Modifier.weight(1f).fillMaxHeight().testTag("home-picks"), picks)
            HomeTile("지난 여행 기록", "함께한 순간을 다시 꺼내봐요", R.drawable.home_memories,
                Icons.Rounded.History, Modifier.weight(1f).fillMaxHeight().testTag("home-history"), history)
        }
        Spacer(Modifier.height(12.dp))
    }
}
@Composable private fun HomeTile(title: String, subtitle: String, image: Int, icon: ImageVector, modifier: Modifier, click: () -> Unit,
    selected: Boolean = false) {
    Box(modifier.border(4.dp, if (selected) Purple else Color.Transparent, RoundedCornerShape(36.dp))
        .padding(4.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xFFE8EAE8)).clickable(onClick = click)) {
        Image(painterResource(image), null, Modifier.fillMaxSize().alpha(.32f), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(.05f), Color.White.copy(.8f)))))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(38.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Label(subtitle, 23, color = Color(0xFF555861)); Spacer(Modifier.height(10.dp)); Label(title, 38, FontWeight.Bold) }
            Icon(icon, null, Modifier.size(64.dp).background(Color.White.copy(.8f), CircleShape).padding(15.dp), tint = Purple)
        }
    }
}
@Composable private fun Setup(state: TravelState, back: () -> Unit, nights: (Int) -> Unit,
    destination: () -> Unit, edit: (Int) -> Unit, next: () -> Unit) {
    var duration by remember { mutableStateOf(false) }
    ReviewTripSetup(state,back,destination,{ duration=true },edit,next)
    if(duration) DurationOverlay(state.nights,{duration=false}) { nights(it);duration=false }
}
@Composable private fun PlaceField(label: String, value: String, modifier: Modifier, empty: Boolean = false, click: (() -> Unit)? = null) {
    Column(modifier.clip(RoundedCornerShape(25.dp)).background(Color.White).then(if (click != null) Modifier.clickable(onClick = click) else Modifier).padding(26.dp)) {
        Label(label, 21, color = Muted)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(value, 30, FontWeight.Bold, if (empty) Color(0xFFA0A3AD) else Ink, Modifier.weight(1f), 1)
            if (click != null) Icon(Icons.Rounded.AutoAwesome, "Gleo AI 또는 직접 입력", Modifier.size(31.dp), tint = Purple)
        }
    }
}
@Composable private fun StopLabel(text: String, endpoint: Boolean = false, placeholder: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 63.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (endpoint) 16.dp else 12.dp).background(if (placeholder) Line else Purple.copy(if (endpoint) 1f else .5f), CircleShape))
        Spacer(Modifier.width(17.dp)); Label(text, 25, color = if (placeholder) Color(0xFFA0A3AD) else Ink, maxLines = 2)
    }
}
@Composable private fun DurationOverlay(initial: Int, close: () -> Unit, save: (Int) -> Unit) {
    var selected by remember { mutableIntStateOf(initial) }
    Overlay("얼마나 함께할까요?", close) {
        Row(Modifier.fillMaxWidth().height(260.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            AndroidView(factory = { context -> NumberPicker(context).apply { minValue = 0; maxValue = 6; value = initial; wrapSelectorWheel = false
                setOnValueChangedListener { _, _, value -> selected = value }; contentDescription = "숙박 일수" } },
                update = { if (it.value != selected) it.value = selected },
                modifier = Modifier.width(160.dp).fillMaxHeight().testTag("nights-wheel"))
            Label("박", 36, FontWeight.Bold); Spacer(Modifier.width(45.dp)); Label("${selected + 1}일", 46, FontWeight.Bold, Purple)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (0..3).forEach { n -> Action("${n}박", { selected = n }, Modifier.weight(1f), secondary = n != selected) }
        }
        Spacer(Modifier.height(25.dp)); Action("일정 적용", { save(selected) }, Modifier.fillMaxWidth().testTag("duration-save"))
    }
}

@Composable private fun Friends(state: TravelState, back: () -> Unit, group: () -> Unit, invite: () -> Unit, next: () -> Unit) {
    ReviewFriends(state,back,group,invite,next)
}
@Composable private fun MemberCard(index: Int, joined: Boolean, invited: Boolean, modifier: Modifier, invite: (() -> Unit)? = null, name: String = listOf("나", "친구 A", "친구 B", "친구 C")[index]) {
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(Color.White).then(if (invite != null) Modifier.clickable(onClick = invite) else Modifier)
        .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(112.dp).background(if (joined) CarColors[index].copy(.14f) else Paper, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (joined) Icons.Rounded.DirectionsCar else Icons.Rounded.Add, if (!joined) "친구 초대" else null, Modifier.size(55.dp), tint = if (joined) CarColors[index] else Muted)
        }
        Spacer(Modifier.height(30.dp)); Label(if (joined || invited) name else "친구 초대", 29, FontWeight.Bold)
        Spacer(Modifier.height(12.dp)); Label(if (joined) if (index == 0) "여행을 만든 나" else "함께할 준비 완료" else if (invited) "참여 기다리는 중" else "+", 21, color = if (joined) Purple else Muted)
    }
}
@Composable private fun InviteOverlay(state: TravelState, close: () -> Unit, send: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    Overlay("친구에게 초대 보내기", close) {
        Label("여행에 함께할 친구의 전화번호를 입력해 주세요.", 25, color = Muted)
        Spacer(Modifier.height(26.dp))
        OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(15) }, Modifier.fillMaxWidth().testTag("invite-phone"),
            placeholder = { Label("전화번호", 28, color = Muted) }, singleLine = true, textStyle = textStyle(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), shape = RoundedCornerShape(22.dp))
        Label(if (state.story != null) "${state.members.getOrNull(state.invited + 1)?.name.orEmpty()}에게 보내는 시연 초대 · 실제 문자는 발송되지 않습니다." else "시연 초대 · 실제 문자는 발송되지 않습니다.\n한 번의 초대로 친구 A·B·C의 참여를 재현합니다.", 21, color = Muted, modifier = Modifier.padding(vertical = 25.dp))
        Action("초대 보내기", send, Modifier.fillMaxWidth().testTag("send-invite"), phone.length >= 8)
    }
}
@Composable private fun Confirmation(state: TravelState, back: () -> Unit, next: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header("모두 모였어요!", "03  함께할 준비가 끝났어요", back)
        Label(state.groupName, 48, FontWeight.Bold, modifier = Modifier.padding(bottom = 28.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) { state.members.forEachIndexed { i, member -> MemberCard(i, true, true, Modifier.weight(1f).fillMaxHeight(), name = member.name) } }
        Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("위치 공유는 이 여행 중에만 사용해요.", 24, color = Muted); Label("참여 완료  ${state.members.size} / ${state.members.size}", 27, FontWeight.Bold, Purple)
        }
        Action("출발 전 확인하기", next, Modifier.fillMaxWidth().testTag("confirm-next"))
    }
}
@Composable private fun Summary(state: TravelState, back: () -> Unit, start: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=34.dp,vertical=20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(back,Modifier.size(64.dp).background(Color.White,CircleShape).testTag("back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack,"뒤로",Modifier.size(29.dp))
            }
            Spacer(Modifier.width(18.dp));Label("출발 전 마지막 확인",22,color=Muted)
            Spacer(Modifier.weight(1f));Label("04 / 04",20,FontWeight.Bold,Purple)
        }
        Spacer(Modifier.height(34.dp))
        Label("우리, 이제 떠나볼까요?",48,FontWeight.ExtraBold)
        Label("출발지는 달라도, 여행은 함께 시작해요.",25,color=Muted,modifier=Modifier.padding(top=8.dp,bottom=28.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFFECE8FF)).padding(30.dp)) {
                Label(state.groupName,24,FontWeight.Bold,Purple)
                Row(Modifier.fillMaxWidth().padding(top=16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(Color.White,CircleShape),contentAlignment=Alignment.Center) {
                        Icon(Icons.Rounded.Flag,null,Modifier.size(30.dp),tint=Purple)
                    }
                    Column(Modifier.weight(1f).padding(start=20.dp)) {
                        Label("함께 도착할 곳",18,color=Muted)
                        Label(state.destination,34,FontWeight.ExtraBold)
                    }
                }
                Row(Modifier.padding(top=22.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    listOf("${state.nights}박 ${state.days.size}일","차량 ${state.members.size}대", "경유지 ${state.days.sumOf { it.stops.size }}곳").forEach { text ->
                        Surface(color=Color.White.copy(.8f),shape=RoundedCornerShape(14.dp)) { Label(text,21,FontWeight.Bold,modifier=Modifier.padding(horizontal=18.dp,vertical=10.dp)) }
                    }
                }
            }
            Label("함께 떠나는 친구들",25,FontWeight.Bold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                state.members.forEachIndexed { index,member ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(52.dp).background(CarColors[index].copy(.12f),CircleShape),contentAlignment=Alignment.Center) {
                            Icon(Icons.Rounded.DirectionsCar,null,Modifier.size(29.dp),tint=CarColors[index])
                        }
                        Label(member.name,28,FontWeight.Bold)
                        Label(member.origin.name,20,color=Muted,maxLines=2)
                        HorizontalDivider(Modifier.padding(vertical=6.dp),color=Line)
                        Label("약 ${JourneyEngine.duration(member.baseMinutes)}",22,FontWeight.Bold)
                    }
                }
            }
            state.story?.let { Label("총 ${it.partySize}명 · 차량당 2명 · 상세 조작은 동승자가 맡아요",19,color=Muted) }
        }
        Spacer(Modifier.height(24.dp))
        Action("함께 여행 시작",start,Modifier.fillMaxWidth().testTag("start-trip"))
    }
}

@Composable private fun InputOverlay(request: InputRequest, services: ConnectServices, state: TravelState, close: () -> Unit, save: (String) -> Unit) {
    var value by remember(request) { mutableStateOf(request.value) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(request) {
        if (request.autoListen) services.requestVoice(request.sample, { value = it; error = "" }, { error = it })
    }
    GleoInputPresentation(request.title, value, services.listening, state, error,
        if (BuildConfig.DEMO_MODE) "시연 음성으로 입력한 후 자유롭게 수정할 수 있어요." else "Pleos 음성 인식 결과를 확인한 뒤 적용해 주세요.",
        { if (services.listening) services.stopListening(); value = it.take(if (request.title.contains("음성")) 240 else 60) },
        { services.requestVoice(request.sample, { value = it; error = "" }, { error = it }) },
        close, { services.stopListening(); save(value.trim()) })
}
@Composable internal fun GleoButton(listening: Boolean, click: () -> Unit) {
    OutlinedButton(click, Modifier.height(56.dp).testTag("gleo"), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Purple.copy(.25f)), contentPadding = PaddingValues(horizontal = 18.dp)) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(22.dp), tint = Purple); Spacer(Modifier.width(8.dp))
        Label(if (listening) "듣고 있어요…" else "Gleo AI", 22, FontWeight.Bold, Purple, maxLines = 1)
    }
}

@Composable private fun DemoViewerBar(state: TravelState, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFF3F4F7)).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("DEMO 화면", 16, FontWeight.Bold, Muted, maxLines = 1)
        state.members.forEach { member ->
            val on = state.viewerId == member.id
            Surface(onClick = { select(member.id) }, shape = RoundedCornerShape(16.dp),
                color = if (on) Purple else Color.White, modifier = Modifier.testTag("viewer-${member.id}")) {
                Label(member.name, 18, FontWeight.Bold, if (on) Color.White else Ink,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        Label("Connect-S-17  ·  1:2 DV", 15, color = Muted, maxLines = 1)
    }
}

@Composable private fun ConnectLightDock() {
    Image(painterResource(R.drawable.connect_light_dock), "Connect light dock",
        Modifier.fillMaxWidth().height(76.dp).background(Color(0xFFF7F8FA)).testTag("connect-dock"),
        contentScale = ContentScale.FillWidth)
}
@Composable internal fun textStyle() = androidx.compose.ui.text.TextStyle(fontFamily = Asta, fontWeight = FontWeight.SemiBold, fontSize = 29.sp, color = Ink)

/** Drawn inside our app window: never dims Pleos Driving View or the system dock. */
@Composable internal fun Overlay(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { close() }
    Box(Modifier.fillMaxSize().background(Ink.copy(.27f)).pointerInput(Unit) { detectTapGestures(onTap = {}) }, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 890.dp).fillMaxWidth(.88f).heightIn(max = 970.dp)
            .shadow(24.dp, RoundedCornerShape(40.dp)).clip(RoundedCornerShape(40.dp)).background(Color.White).padding(36.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(title, 35, FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(close, Modifier.size(65.dp)) { Icon(Icons.Rounded.Close, "닫기", Modifier.size(32.dp)) }
            }
            content()
        }
    }
}

@Composable private fun ItineraryOverlay(day: Int, state: TravelState, services: ConnectServices, close: () -> Unit, save: (List<Place>) -> Unit) {
    PlanningItineraryEditor(day, state, services, close, save)
}

@Composable private fun HistoryScreen(state: TravelState, back: () -> Unit, resume: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header("지난 여행 기록", "함께한 길이 우리의 이야기가 돼요", back)
        if (!state.started) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Route, null, Modifier.size(80.dp), tint = Purple); Spacer(Modifier.height(24.dp))
            Label("아직 지난 여행이 없어요", 36, FontWeight.Bold); Label("첫 여행을 떠나고 추억을 남겨보세요.", 25, color = Muted)
        } else {
            Label("진행 중인 여행", 27, color = Muted, modifier = Modifier.padding(vertical = 24.dp))
            PlaceField(state.groupName, state.destination, Modifier.fillMaxWidth(), click = resume)
        }
    }
}
