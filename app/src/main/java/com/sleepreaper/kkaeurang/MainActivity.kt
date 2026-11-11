package com.sleepreaper.kkaeurang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepreaper.kkaeurang.ui.theme.KkaeUrangTheme
import com.sleepreaper.kkaeurang.voice.VoiceInputResult
import com.sleepreaper.kkaeurang.voice.VoiceModule

private const val ASK_QUESTION = "주행이 평소와 조금 달라요. 괜찮으세요?"

/** "질문하고 듣기" 테스트 진행 상태 */
enum class AskState { IDLE, ASKING, LISTENING }

class MainActivity : ComponentActivity() {

    private lateinit var voiceModule: VoiceModule

    // 테스트 화면 상태 (VoiceModule 콜백은 메인 스레드로 오므로 바로 바꿔도 된다)
    private var askState by mutableStateOf(AskState.IDLE)
    private var lastResult by mutableStateOf<VoiceInputResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceModule = VoiceModule(this)
        voiceModule.init()

        enableEdgeToEdge()
        setContent {
            KkaeUrangTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceTestScreen(
                        askState = askState,
                        lastResult = lastResult,
                        onAskClick = { askAndListen() },
                        modifier = Modifier.padding(innerPadding)
                    )
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

    override fun onDestroy() {
        voiceModule.release()
        super.onDestroy()
    }
}

@Composable
fun VoiceTestScreen(
    askState: AskState,
    lastResult: VoiceInputResult?,
    onAskClick: () -> Unit,
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
            onAskClick = {}
        )
    }
}
