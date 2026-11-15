package com.pleosfriends

import android.Manifest
import android.app.Activity
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.vehicle.Vehicle
import java.util.Locale

/** Only published Pleos SDKs and AAOS APIs. No Gleo Schema, LLM, or cabin-audio export. */
class ConnectServices(private val activity: Activity) {
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    val policyKnown get() = DrivingPolicy.known
    val restricted get() = DrivingPolicy.restricted
    var status by mutableStateOf(if (BuildConfig.DEMO_MODE) "시연 모드" else "Pleos 연결 준비 중"); private set
    var listening by mutableStateOf(false); private set
    var myPosition by mutableStateOf<Place?>(null); private set
    var destinationSeconds by mutableStateOf<Int?>(null); private set
    private var foreground = true
    private var navi: NaviHelper? = null
    private var naviReady = false
    private var pendingRoute: TravelState? = null
    private var location: FusedLocation? = null
    private var locationRegistered = false
    private var stt: SpeechToText? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var vehicle: Vehicle? = null
    private var voiceResult: ((String) -> Unit)? = null
    private var voiceError: ((String) -> Unit)? = null
    private var voiceReady = false
    private var voiceRequestId = 0L
    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var preroll: MediaPlayer? = null
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttrs).setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopSpeech()
        }.build()
    private val navListener = object : NaviHelperEventListener {
        override fun onRoutingCompleted() { navi?.getDestinationInfo(); main.post { status = "내비게이션 경로 반영 완료" } }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) { main.post { destinationSeconds = destinationInfo.remainTime } }
        override fun onError(errorCode: NaviErrorCode) { main.post { status = "내비게이션 요청 실패 · 다시 시도해 주세요" } }
    }
    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) { main.post { status = "현재 위치를 확인할 수 없어요" }; return }
            main.post { myPosition = Place("나", "Pleos 차량 위치", location.latitude, location.longitude) }
        }
        override fun onError(exception: Exception) { main.post { status = "위치 서비스 연결을 확인해 주세요" } }
    }
    private val recognitionListener = object : ResultListener {
        override fun onReady() { main.post { listening = true } }
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            if (completed) main.post { listening = false; voiceResult?.invoke(stt); stopListening() }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() { main.post { listening = false } }
        override fun onError(errorType: ai.pleos.playground.stt.constant.ErrorType) {
            main.post { listening = false; voiceError?.invoke("음성을 인식하지 못했어요. 직접 입력해 주세요.") }
        }
    }
    fun initialize() {
        DrivingPolicy.initialize(activity.applicationContext)
        if (BuildConfig.DEMO_MODE) return
        runCatching {
            navi = NaviHelper(activity.applicationContext).also { helper ->
                helper.initialize(onConnected = {
                    helper.addListener(navListener)
                    main.post { naviReady = true; status = "Pleos Connect 연결됨"; pendingRoute?.let(::startNavigation) }
                }, onFailed = { main.post { status = "내비게이션 서비스에 연결할 수 없어요" } })
            }
            stt = SpeechToText(activity.applicationContext, ai.pleos.playground.stt.constant.Mode.ON_DEVICE).also {
                it.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
                    override fun onSuccess() { main.post { voiceReady = true } }
                    override fun onFailure(errorType: ai.pleos.playground.stt.constant.ErrorType) { main.post { voiceReady = false } }
                })
                it.addListener(recognitionListener)
                it.setLocale(Locale.KOREA)
            }
            tts = TextToSpeech(activity.applicationContext, ai.pleos.playground.tts.constant.Mode.ON_DEVICE).also {
                it.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
                    override fun onSuccess() { main.post { ttsReady = true } }
                    override fun onFailure(errorType: ai.pleos.playground.tts.constant.ErrorType) { main.post { ttsReady = false } }
                })
                it.setLocale(Locale.KOREA)
                it.setAudioAttributes(audioAttrs)
                it.addEventListener(object : ai.pleos.playground.tts.listener.EventListener {
                    override fun onReady() = Unit
                    override fun onStart() = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                    override fun onDone() { audio.abandonAudioFocusRequest(focus) }
                    override fun onStop() { audio.abandonAudioFocusRequest(focus) }
                    override fun onError(errorType: ai.pleos.playground.tts.constant.ErrorType) { audio.abandonAudioFocusRequest(focus) }
                })
            }
        }.onFailure { status = "일부 Pleos 기능에 연결할 수 없어요" }
        runCatching {
            vehicle = Vehicle(activity.applicationContext).also { helper ->
                helper.initialize()
                helper.getDrivingMode().getCurrentDrivingModeState(
                    onComplete = { main.post { if (status.contains("Connect")) status = "Pleos Connect · Vehicle 연결됨" } },
                    onFailed = { }
                )
            }
        }
    }
    fun requestVoice(sample: String, result: (String) -> Unit, error: (String) -> Unit) {
        if (restricted || !policyKnown) { error("주차 후 입력해 주세요."); return }
        stopListening(); voiceResult = result; voiceError = error
        val requestId = voiceRequestId
        if (BuildConfig.DEMO_MODE) {
            listening = true
            main.postDelayed({ if (requestId == voiceRequestId && listening && foreground) { listening = false; result(sample) } }, 650)
        } else if (voiceReady) runCatching { listening = true; stt?.request() }
            .onFailure { listening = false; error("음성 서비스를 사용할 수 없어요. 직접 입력해 주세요.") }
        else error("음성 서비스 준비 중이에요. 직접 입력할 수 있어요.")
    }
    fun stopListening() { voiceRequestId++; listening = false; runCatching { stt?.stop() }; voiceResult = null; voiceError = null }
    fun startLocation() {
        if (BuildConfig.DEMO_MODE || locationRegistered) return
        if (activity.checkSelfPermission("pleos.car.permission.FUSED_LOCATION") != PackageManager.PERMISSION_GRANTED) {
            status = "위치 공유를 위해 위치 권한이 필요해요"; return
        }
        runCatching {
            val fused = location ?: FusedLocation(activity.applicationContext).also { location = it; if (it.isSupported()) it.initialize() }
            if (fused.isSupported()) { fused.registerFusedLocationCallback(locationListener); locationRegistered = true }
            else status = "이 환경은 차량 위치를 지원하지 않아요"
        }.onFailure { status = "위치 서비스 연결을 확인해 주세요" }
    }
    fun startNavigation(state: TravelState) {
        if (BuildConfig.DEMO_MODE) return
        pendingRoute = state
        if (!naviReady) { status = "내비게이션 연결 후 경로를 요청합니다"; return }
        val dest = DemoPlaces.resolve(state.destination)
        // No silent geocoding: connected requests accept only the known showcase destination.
        if (state.destination != DemoPlaces.destination.name) { status = "직접 입력한 목적지는 내비에서 위치 확인이 필요해요"; return }
        runCatching {
            val target = RouteInfo(latitude = dest.lat, longitude = dest.lon, poiName = dest.name,
                poiId = "", poiSubId = "", address = dest.address)
            val stops = JourneyEngine.visibleStops(state, state.members.first().id, state.activeDay)
                .filterNot { it.name in state.completedStops }
            if (stops.size > 2) { status = "내비게이션은 경유지 2개까지 지원해요"; return }
            if (stops.any { it.name.contains("휴게") || it.address.contains("시연") || it.address.contains("직접 입력") }) {
                status = "시연 경유지는 실제 내비에 전송하지 않아요. 위치 확인이 필요해요"; return
            }
            if (stops.isEmpty()) navi?.requestRoute(target)
            else navi?.requestRouteWithWaypoints(RouteWithWaypointsRequest(target, stops.mapIndexed { i, p ->
                RequestWaypointInfo(latitude = p.lat, longitude = p.lon, poiName = p.name, poiId = "",
                    address = p.address, poiSubId = "", waypointIndex = if (i == 0) WaypointIndex.FIRST else WaypointIndex.SECOND)
            }))
            status = "내비게이션 경로 요청 중"
            pendingRoute = null
        }.onFailure { status = "경로를 요청하지 못했어요" }
    }
    fun announce(text: String) {
        if (BuildConfig.DEMO_MODE && foreground) {
            val sample = when {
                text.contains("sorry", true) -> R.raw.demo_bathroom
                text.contains("화장실") -> R.raw.demo_rest
                text.contains("우리도 잠깐") -> R.raw.demo_dohyun_reply
                text.contains("큰 카페") -> R.raw.demo_cafe
                text.contains("부산 도착 기념") -> R.raw.demo_seoyun_reply
                else -> null
            }
            if (sample != null && audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                preroll?.release()
                preroll = MediaPlayer.create(activity,sample)?.apply {
                    setOnCompletionListener { player -> player.release(); preroll = null; audio.abandonAudioFocusRequest(focus) }
                    start()
                }
            }
            return
        }
        if (!foreground || !ttsReady) return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        // Real 180 ms PCM silence, not a timer, primes the audio output before the SDK speaks.
        preroll?.release()
        preroll = MediaPlayer.create(activity, R.raw.voice_preroll)?.apply {
            setOnCompletionListener { player -> player.release(); preroll = null; if (foreground) tts?.speak(text) }
            start()
        }
    }
    private fun stopSpeech() { runCatching { preroll?.release() }; preroll = null; runCatching { tts?.stop() }; audio.abandonAudioFocusRequest(focus) }
    fun onForeground(active: Boolean, tripActive: Boolean) {
        foreground = active
        if (!active) { stopListening(); stopSpeech(); stopLocation() }
        else if (tripActive) startLocation()
    }
    private fun stopLocation() {
        if (locationRegistered) runCatching { location?.unregisterFusedLocationCallback(locationListener) }
        locationRegistered = false
    }
    fun close() {
        if (closed) return
        closed = true
        onForeground(false, false)
        runCatching { navi?.removeListener(navListener); navi?.release() }
        runCatching { location?.release() }
        runCatching { stt?.removeListener(recognitionListener); stt?.release() }
        runCatching { tts?.release() }
        runCatching { vehicle?.release() }
        vehicle = null
    }
}
