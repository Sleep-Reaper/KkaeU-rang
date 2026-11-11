package com.sleepreaper.kkaeurang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepreaper.kkaeurang.ui.theme.KkaeUrangTheme
import com.sleepreaper.kkaeurang.voice.TtsManager

private const val TEST_SENTENCE = "주행이 평소와 조금 달라요. 괜찮으세요?"

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsManager = TtsManager(this)
        ttsManager.init()

        enableEdgeToEdge()
        setContent {
            KkaeUrangTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SpeakTestScreen(
                        onSpeakClick = { ttsManager.speak(TEST_SENTENCE) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        ttsManager.release()
        super.onDestroy()
    }
}

@Composable
fun SpeakTestScreen(onSpeakClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = onSpeakClick) {
            Text(
                text = "말하기 테스트",
                fontSize = 36.sp,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SpeakTestScreenPreview() {
    KkaeUrangTheme {
        SpeakTestScreen(onSpeakClick = {})
    }
}
