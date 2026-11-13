package com.sleepreaper.kkaeurang

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepreaper.kkaeurang.integration.DrowsinessService
import com.sleepreaper.kkaeurang.integration.KkaeuRuntime
import com.sleepreaper.kkaeurang.ui.fake.FakeKkaeuStateSource
import com.sleepreaper.kkaeurang.ui.screens.KkaeuApp
import com.sleepreaper.kkaeurang.ui.theme.KkaeUrangTheme
import com.sleepreaper.kkaeurang.voice.StopReason
import com.sleepreaper.kkaeurang.voice.VoiceInputResult
import com.sleepreaper.kkaeurang.voice.VoiceModule

private const val ASK_QUESTION = "주행이 평소와 조금 달라요. 괜찮으세요?"
private const val TAG = "Kkaeu.Main"

/** 화면에 무엇을 띄울지 */
private enum class AppMode {
    LIVE,            // 실차: 판단 엔진 + 음성 + 실제 차량 신호 → 깨우랑 UI
    LIVE_SIMULATED,  // 에뮬레이터: 판단 엔진 + 음성은 실제, 차량 신호만 가짜 (엔진 디버그 패널로 주행 시나리오 재생)
    FAKE_DEMO,       // 가짜 데이터로 UI만 시연 (디버그 패널·데모 시나리오 사용 가능, 엔진은 돌지 않음)
    VOICE_TEST,      // 기존 음성 모듈 테스트 화면
}

/** 시연·개발 때 여기만 바꾼다. 실차에서는 LIVE */
private val APP_MODE = AppMode.LIVE_SIMULATED

/** 실제 모드(LIVE·LIVE_SIMULATED)에서 로고(헤더 가운데)나 계기판을 길게 누르면 엔진 디버그 패널을 연다. 배포 때는 false */
private const val LIVE_DEBUG_PANEL = true

// 차량 신호 수집·차량 제어에 필요한 실행 중 권한 (Manifest에도 선언되어 있어야 함)
private val CAR_PERMISSIONS = arrayOf(
    "android.car.permission.CAR_SPEED",
    "android.car.permission.CAR_POWERTRAIN",
    "pleos.car.permission.CAR_DRIVE",
    "pleos.car.permission.CAR_LIGHTS",
    // 창문 열기·찬바람. 거부되면 창문을 열지 못하고 엔진이 음성으로 대체 안내한다
    "pleos.car.permission.CAR_WINDOWS",
    "pleos.car.permission.CONTROL_CAR_WINDOWS",
    "pleos.car.permission.CAR_CLIMATE",
    "pleos.car.permission.CONTROL_CAR_CLIMATE"
)

// 포그라운드 서비스 상시 알림용 (Android 13+). 거부돼도 서비스는 동작한다
private val NOTIFICATION_PERMISSIONS =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

// 마이크: 음성 대답 듣기 + 마이크 타입 포그라운드 서비스 시작 조건. 거부되면 서비스를 시작하지 않는다
private const val MIC_PERMISSION = Manifest.permission.RECORD_AUDIO

/** "질문하고 듣기" 테스트 진행 상태 */
enum class AskState { IDLE, ASKING, LISTENING }

class MainActivity : ComponentActivity() {

    // VOICE_TEST 모드에서만 쓴다. LIVE 모드의 VoiceModule은 DrowsinessService가 들고 있다.
    private val voiceModule: VoiceModule by lazy { VoiceModule(this).also { it.init() } }

    // FAKE_DEMO 모드용 가짜 상태 공급자 (처음 접근할 때 만들어진다)
    private val fakeSource: FakeKkaeuStateSource by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val denied = granted.filterValues { !it }.keys
            if (denied.isNotEmpty()) Log.e(TAG, "권한 거부: $denied")
            if (MIC_PERMISSION in denied) {
                // 마이크 없이는 마이크 타입 서비스를 시작할 수 없고 음성 질문도 못 하므로 시작하지 않는다
                Log.e(TAG, "마이크 권한 거부 → 졸음 감시 서비스를 시작하지 않음")
                return@registerForActivityResult
            }
            // 차량 권한 일부가 거부돼도 시작한다. 어떤 신호가 안 들어오는지 로그로 확인하기 위함.
            startDrowsinessService(simulated = APP_MODE == AppMode.LIVE_SIMULATED)
        }

    // 테스트 화면 상태 (VoiceModule 콜백은 메인 스레드로 오므로 바로 바꿔도 된다)
    private var askState by mutableStateOf(AskState.IDLE)
    private var lastResult by mutableStateOf<VoiceInputResult?>(null)
    private var alarmRunning by mutableStateOf(false)
    private var alarmStopReason by mutableStateOf<StopReason?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 창문·공조(cabin)와 내비 모듈은 DrowsinessService가 KkaeuLiveSource(cabin = ..., navi = ...)로 넘긴다
        // 이미 서비스가 돌고 있으면(화면 회전·재진입) 다시 시작하지 않는다
        if (KkaeuRuntime.source.value == null) {
            when (APP_MODE) {
                AppMode.LIVE ->
                    permissionLauncher.launch(arrayOf(MIC_PERMISSION) + NOTIFICATION_PERMISSIONS + CAR_PERMISSIONS)
                // 가짜 차량 신호는 차량 권한이 필요 없지만, 음성은 실제라 마이크·알림 권한은 필요하다
                AppMode.LIVE_SIMULATED ->
                    permissionLauncher.launch(arrayOf(MIC_PERMISSION) + NOTIFICATION_PERMISSIONS)
                else -> Unit
            }
        }

        enableEdgeToEdge()
        setContent {
            KkaeUrangTheme {
                when (APP_MODE) {
                    AppMode.LIVE, AppMode.LIVE_SIMULATED -> {
                        // 서비스가 엔진을 만든 뒤에 source가 생긴다 (그 전이나 서비스 종료 후에는 null)
                        val source by KkaeuRuntime.source.collectAsState()
                        val live = source
                        if (live == null) {
                            PreparingScreen()
                        } else {
                            val state by live.state.collectAsState()
                            KkaeuApp(
                                state = state,
                                actions = live,
                                debugControls = null,
                                liveDebugControls = live.takeIf { LIVE_DEBUG_PANEL },
                            )
                        }
                    }
                    AppMode.FAKE_DEMO -> {
                        val state by fakeSource.state.collectAsState()
                        KkaeuApp(state = state, actions = fakeSource, debugControls = fakeSource)
                    }
                    AppMode.VOICE_TEST -> Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        VoiceTestScreen(
                            askState = askState,
                            lastResult = lastResult,
                            alarmRunning = alarmRunning,
                            alarmStopReason = alarmStopReason,
                            onAskClick = { askAndListen() },
                            onAlarmStartClick = { startAlarm() },
                            onAlarmStopClick = { voiceModule.stopAlarm() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    private fun askAndListen() {
        askState = AskState.ASKING
        voiceModule.askAndListen(
            ASK_QUESTION,
            onListening = { askState = AskState.LISTENING }
        ) { result ->
            lastResult = result
            askState = AskState.IDLE
        }
    }

    // 졸음 판단 로직이 "졸음 상태 진입"을 알린 상황을 흉내 낸다 (기본값 그대로)
    private fun startAlarm() {
        voiceModule.startAlarm { reason ->
            alarmRunning = false
            alarmStopReason = reason
        }
        // 메인 스레드에서 부르면 상태가 바로 반영된다. 이미 경고 중이었거나 무음 상태면 그에 맞게 표시된다
        if (voiceModule.isAlarmRunning) {
            alarmRunning = true
            alarmStopReason = null
        }
    }

    private fun startDrowsinessService(simulated: Boolean) {
        try {
            DrowsinessService.start(this, simulated)
        } catch (e: Exception) {
            Log.e(TAG, "졸음 감시 서비스 시작 실패", e)
        }
    }

    override fun onDestroy() {
        // 엔진과 음성은 서비스가 들고 있으므로 여기서 멈추지 않는다 (화면을 닫아도 계속 감시)
        if (APP_MODE == AppMode.VOICE_TEST) voiceModule.release()
        super.onDestroy()
    }
}

/** 서비스가 엔진을 만들기 전(권한 요청 중 포함)이나 서비스가 종료된 뒤에 보이는 화면 */
@Composable
private fun PreparingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "졸음 감시 준비 중…", fontSize = 28.sp)
    }
}

@Composable
fun VoiceTestScreen(
    askState: AskState,
    lastResult: VoiceInputResult?,
    alarmRunning: Boolean,
    alarmStopReason: StopReason?,
    onAskClick: () -> Unit,
    onAlarmStartClick: () -> Unit,
    onAlarmStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TestButton(label = "질문하고 듣기", onClick = onAskClick)
        ResultPanel(askState = askState, result = lastResult)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TestButton(label = "음성 경고 시작", onClick = onAlarmStartClick)
            TestButton(label = "음성 경고 중지", onClick = onAlarmStopClick)
        }
        Text(text = alarmStatusText(alarmRunning, alarmStopReason), fontSize = 28.sp)
    }
}

@Composable
private fun TestButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(
            text = label,
            fontSize = 36.sp,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
        )
    }
}

@Composable
private fun ResultPanel(askState: AskState, result: VoiceInputResult?) {
    val lines = when (askState) {
        AskState.ASKING -> listOf("질문하는 중…")
        AskState.LISTENING -> listOf("지금 말하세요 (듣는 중…)")
        AskState.IDLE -> if (result == null) {
            listOf("아직 결과 없음")
        } else {
            listOf(
                "인식된 텍스트: ${result.text ?: "(없음)"}",
                "말 시작까지: ${result.speechStartTimeMs?.let { "$it ms" } ?: "(말소리 없음)"}",
                "응답 시간: ${result.responseTimeMs} ms (마이크 열린 뒤부터)",
                "무응답: ${if (result.isTimeout) "예" else "아니오"}"
            )
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        lines.forEach { Text(text = it, fontSize = 28.sp) }
    }
}

private fun alarmStatusText(running: Boolean, stopReason: StopReason?): String = when {
    running -> "음성 경고: 진행 중"
    stopReason == null -> "음성 경고: 대기"
    else -> {
        val description = when (stopReason) {
            StopReason.STOPPED_BY_CALLER -> "호출자가 중지"
            StopReason.MAX_DURATION -> "최대 시간 초과"
            StopReason.AUDIO_FOCUS_LOST -> "다른 소리에 오디오 포커스를 뺏김"
            StopReason.SILENCED -> "무음 상태"
        }
        "음성 경고: 중단됨 (사유: ${stopReason.name} · $description)"
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 600)
@Composable
fun VoiceTestScreenPreview() {
    KkaeUrangTheme {
        VoiceTestScreen(
            askState = AskState.IDLE,
            lastResult = VoiceInputResult(
                text = "네 괜찮아요",
                responseTimeMs = 2650L,
                isTimeout = false,
                speechStartTimeMs = 1240L
            ),
            alarmRunning = false,
            alarmStopReason = StopReason.STOPPED_BY_CALLER,
            onAskClick = {},
            onAlarmStartClick = {},
            onAlarmStopClick = {}
        )
    }
}
