package com.example.kkaeu_rang.detector

/** 음성 인식 결과를 [DriverResponse]로 분류한다. 순수 함수라 단위 테스트가 쉽다. */
object DriverResponseClassifier {

    // "안 졸려", "졸리지 않아"처럼 졸림을 부정하는 표현 → 괜찮은 것으로 본다. (공백 제거 후 비교)
    private val NOT_SLEEPY = listOf(
        "안졸", "안피곤", "안힘들", "졸리지않", "졸지않", "피곤하지않", "힘들지않"
    )

    // 졸림·피로 인정, 또는 괜찮지 않다는 표현
    private val ADMIT = listOf(
        "졸", "피곤", "힘들", "안괜찮", "괜찮지않", "안좋", "별로"
    )

    fun classify(answer: VoiceAnswer): DriverResponse {
        val text = answer.text
        if (text != null) {
            val t = text.replace(" ", "")
            return when {
                NOT_SLEEPY.any { t.contains(it) } -> DriverResponse.OK   // 부정 표현을 먼저 걸러야 한다
                ADMIT.any { t.contains(it) } -> DriverResponse.ADMITTED
                t.contains("괜찮") -> DriverResponse.OK                  // 오인식("그 나는 괜찮아?")도 포함
                else -> DriverResponse.UNCLEAR
            }
        }
        return when {
            answer.isTimeout && answer.speechStartTimeMs != null -> DriverResponse.SLOW
            answer.isTimeout -> DriverResponse.NO_ANSWER
            else -> DriverResponse.ERROR
        }
    }
}
