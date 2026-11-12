package com.sleepreaper.kkaeurang

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
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
import androidx.lifecycle.lifecycleScope
import com.sleepreaper.kkaeurang.integration.KkaeuLiveSource
import com.sleepreaper.kkaeurang.integration.SimulatedSignalSource
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

/** 실제 모드(LIVE·LIVE_SIMULATED)에서 계기판을 길게 누르면 엔진 디버그 패널을 연다. 배포 때는 false */
private const val LIVE_DEBUG_PANEL = true

// 차량 신호 수집에 필요한 실행 중 권한 (Manifest에도 선언되어 있어야 함)
private val CAR_PERMISSIONS = arrayOf(
    "android.car.permission.CAR_SPEED",
    "android.car.permission.CAR_POWERTRAIN",
    "pleos.car.permission.CAR_DRIVE",
    "pleos.car.permission.CAR_LIGHTS"
)

/** "질문하고 듣기" 테스트 진행 상태 */
enum class AskState { IDLE, ASKING, LISTENING }

class MainActivity : ComponentActivity() {

    private lateinit var voiceModule: VoiceModule

    // 실제 모듈을 UI에 연결하는 상태 공급자. 안에서 졸음 판단 엔진(DrowsinessChecker)을 만들고 돌린다.
    private var liveSource: KkaeuLiveSource? = null

    // FAKE_DEMO 모드용 가짜 상태 공급자 (처음 접근할 때 만들어진다)
    private val fakeSource: FakeKkaeuStateSource by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val denied = granted.filterValues { !it }.keys
            if (denied.isNotEmpty()) Log.e(TAG, "권한 거부: $denied")
            // 일부 권한이 거부돼도 시작한다. 어떤 신호가 안 들어오는지 로그로 확인하기 위함.
            startDrowsinessChecker()
        }

    // 테스트 화면 상태 (VoiceModule 콜백은 메인 스레드로 오므로 바로 바꿔도 된다)
    private var askState by mutableStateOf(AskState.IDLE)
    private var lastResult by mutableStateOf<VoiceInputResult?>(null)
    private var alarmRunning by mutableStateOf(false)
    private var alarmStopReason by mutableStateOf<StopReason?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceModule = VoiceModule(this)
        voiceModule.init()

        // 창문·내비 모듈이 생기면 KkaeuLiveSource(window = ..., navi = ...)로 넘긴다
        when (APP_MODE) {
            AppMode.LIVE -> {
                liveSource = KkaeuLiveSource(this, voiceModule, lifecycleScope)
                permissionLauncher.launch(CAR_PERMISSIONS)
            }
            AppMode.LIVE_SIMULATED -> {
                // 가짜 차량 신호는 차량 권한이 필요 없으므로 바로 시작한다
                liveSource = KkaeuLiveSource(this, voiceModule, lifecycleScope, signalSource = SimulatedSignalSource())
                startDrowsinessChecker()
            }
            else -> Unit
        }

        enableEdgeToEdge()
        setContent {
            KkaeUrangTheme {
                when (APP_MODE) {
                    AppMode.LIVE, AppMode.LIVE_SIMULATED -> {
                        val source = requireNotNull(liveSource)
                        val state by source.state.collectAsState()
                        KkaeuApp(
                            state = state,
                            actions = source,
                            debugControls = null,
                            liveDebugControls = source.takeIf { LIVE_DEBUG_PANEL },
                        )
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

    private fun startDrowsinessChecker() {
        try {
            liveSource?.start()
        } catch (e: Exception) {
            Log.e(TAG, "졸음 판단 시작 실패", e)
        }
    }

    override fun onDestroy() {
        liveSource?.stop()   // 엔진을 voiceModule보다 먼저 정리
        voiceModule.release()
        super.onDestroy()
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
