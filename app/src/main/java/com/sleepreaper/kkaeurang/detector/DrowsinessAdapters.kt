package com.sleepreaper.kkaeurang.detector

import android.util.Log
import com.sleepreaper.kkaeurang.voice.VoiceModule

/*
 * DrowsinessChecker가 부르는 인터페이스(DrowsinessActions.kt)를 팀원 모듈에 연결하는 얇은 클래스들.
 * 팀원 모듈의 이름이나 함수가 바뀌면 이 파일만 고치면 된다.
 */

/** 운전자 질문: [VoiceModule.askAndListen]을 감싼다. 결과 콜백은 메인 스레드로 온다. */
class VoiceModuleDriverVoice(private val voice: VoiceModule) : DriverVoice {

    override fun ask(question: String, onResult: (VoiceAnswer) -> Unit) {
        voice.askAndListen(question) { result ->
            onResult(
                VoiceAnswer(
                    text = result.text,
                    isTimeout = result.isTimeout,
                    speechStartTimeMs = result.speechStartTimeMs
                )
            )
        }
    }

    /** 진행 중인 질문·듣기와 음성 경고까지 모두 멈춘다 (VoiceModule.stopAll 동작). */
    override fun stopAll() = voice.stopAll()

    override fun say(text: String) = voice.speak(text)
}

/** 알람: 음성 경고(문구 반복 + 신호음)를 쓴다. 경고 중이 아닐 때 stop은 무시된다. */
class VoiceModuleAlarm(private val voice: VoiceModule) : AlarmModule {
    override fun start() = voice.startAlarm()
    override fun stop() = voice.stopAlarm()
}

/**
 * 창문·공조 제어([CabinControl])가 없을 때 쓰는 대체 창문 모듈. 호출됐다는 로그만 남긴다.
 * 실제 창문 조절은 KkaeuLiveSource가 CabinControl(실차: PleosCabinControl, 에뮬레이터: SimulatedCabinControl)로 한다.
 */
class NoOpWindowModule : WindowModule {
    override fun open() {
        Log.w(TAG, "창문 열기 호출됨 (창문 제어 없음: 실제로는 열리지 않음)")
    }

    /** 실제로 열지 못하므로 실패로 알린다 (엔진은 로그만 남기고, 화면은 창문 닫힘으로 보여 준다) */
    override fun open(onResult: (Boolean) -> Unit) {
        open()
        onResult(false)
    }
}

/** 졸음쉼터 내비 안내 — 아직 구현되지 않음. 호출됐다는 로그만 남긴다. */
class NoOpNaviModule : NaviModule {
    override fun guideToRestArea() {
        Log.w(TAG, "졸음쉼터 안내 호출됨 (미구현: 실제로는 안내되지 않음)")
    }

    /** 실제로 안내하지 못하므로 실패로 알린다 (엔진이 "졸음쉼터 안내에 실패했어요"라고 말한다) */
    override fun guideToRestArea(onResult: (Boolean) -> Unit) {
        guideToRestArea()
        onResult(false)
    }
}

private const val TAG = "Kkaeu.Adapter"
