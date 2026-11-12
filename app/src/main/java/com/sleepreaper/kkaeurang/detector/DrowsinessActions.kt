package com.sleepreaper.kkaeurang.detector

/*
 * DrowsinessChecker가 밖으로 호출하는 기능의 약속(인터페이스) 모음.
 * 팀원 모듈(음성·알람·창문·내비)은 이 인터페이스를 구현하는 얇은 연결 클래스(어댑터)로 붙인다.
 * 이렇게 두면 패키지/클래스 이름이 바뀌어도 어댑터만 고치면 되고, 엔진은 단위 테스트가 가능하다.
 *
 * 모든 호출은 메인 스레드에서 일어난다.
 */

/** 차량 신호 공급원. 구현체: [VehicleSignalCollector] */
interface SignalSource {
    fun start()
    fun stop()
    /** 지금 시점의 최신값을 샘플 한 장으로 만든다. */
    fun sample(now: Long): Sample
}

/** 음성 모듈이 돌려주는 결과 중 판단에 필요한 부분만 옮긴 것 (VoiceInputResult에 대응). */
data class VoiceAnswer(
    val text: String?,
    val isTimeout: Boolean,
    val speechStartTimeMs: Long?
)

/** 질문 → 대답 인식. 구현체: 팀원 VoiceModule을 감싸는 어댑터 */
interface DriverVoice {
    /** 질문하고 대답을 듣는다. 결과는 메인 스레드에서 질문당 1회 [onResult]로 온다. */
    fun ask(question: String, onResult: (VoiceAnswer) -> Unit)

    /** 진행 중인 질문·듣기를 취소한다. 취소되면 [onResult]는 호출되지 않는다. */
    fun stopAll()
}

interface AlarmModule {
    fun start()
    fun stop()
}

interface WindowModule {
    fun open()
}

interface NaviModule {
    fun guideToRestArea()
}

/** 운전자 대답을 판단한 결과 */
enum class DriverResponse {
    ADMITTED,   // 졸림·피로를 직접 말함
    OK,         // 괜찮다고 함
    UNCLEAR,    // 말은 했지만 판단할 단서가 없음
    SLOW,       // 말은 시작했으나 시간 안에 끝내지 못함
    NO_ANSWER,  // 무응답
    ERROR       // 기기·엔진 문제로 인식 실패 → 졸음 판단 보류
}
