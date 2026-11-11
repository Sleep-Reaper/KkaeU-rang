package com.sleepreaper.kkaeurang.voice

/**
 * askAndListen() 한 번의 결과. 졸음 판단 담당에게 그대로 넘기는 값이며, 내용 판단은 하지 않는다.
 *
 * | text   | isTimeout | 의미                                                        |
 * |--------|-----------|-------------------------------------------------------------|
 * | 문장   | false     | 대답을 인식함                                                |
 * | null   | true      | 무응답 (마이크가 열린 뒤 시간 초과, 또는 빈 문장으로 끝남)       |
 * | null   | false     | 인식 실패 (질문을 못 했거나, 엔진이 준비되지 않았거나, 에러)      |
 *
 * 시간 값은 모두 마이크가 열린(듣기 시작) 시점이 기준이며, 엔진 준비 시간은 빠져 있다.
 * - speechStartTimeMs: 언제 말을 시작했는지 (반응 속도에 가까움)
 * - responseTimeMs: 언제 대답이 끝났다고 확정됐는지 (말한 길이 + 끝 감지 시간 포함)
 * 무응답인데 speechStartTimeMs가 있으면 "말은 시작했지만 제한 시간 안에 끝내지 못함"이다.
 */
data class VoiceInputResult(
    val text: String?,        // 인식된 문장 그대로. 무응답이면 null
    val responseTimeMs: Long, // 마이크가 열린(듣기 시작) 시점부터 최종 인식까지 걸린 시간. 엔진 준비 시간은 빠짐
    val isTimeout: Boolean,   // 시간 내 대답이 없었는지
    // 마이크가 열린 시점부터 첫 마디가 글자로 처음 인식될 때까지 걸린 시간 (엔진 인식 지연 수백 ms 포함).
    // 말소리가 인식되지 않았으면 null
    val speechStartTimeMs: Long? = null
)
