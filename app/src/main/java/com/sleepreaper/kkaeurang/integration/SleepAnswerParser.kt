package com.sleepreaper.kkaeurang.integration

import com.sleepreaper.kkaeurang.ui.model.SleepRange

/**
 * 출발 전 질문 "마지막으로 몇 시간 주무셨어요?"에 대한 말 대답 → [SleepRange] (마지막 수면 시간).
 *
 * 알아듣는 말 (띄어쓰기 무시):
 * - 숫자 + 시간: "6시간", "5시간 반", "여섯 시간 잤어요", "4시간밖에 못 잤어"(= 4시간), "열 시간"
 * - 구간: "4에서 6시간", "6~8시간" → 가운데 값, "8시간 이상"·"넘게" → 그 시간 이상,
 *   "4시간 미만"·"4시간도 못 잤어"·"6시간 안 됐어" → 그 시간 미만
 * - 숫자 없이 "한숨도 못 잤어", "거의 못 잤어", "얼마 못 잤어" → 4시간 미만
 * 잠든 시각("새벽 2시에 잤어")은 몇 시간 잤는지 알 수 없어 알아듣지 않는다 (화면 버튼으로 고르게 한다).
 */
object SleepAnswerParser {

    fun parse(text: String?): SleepRange? {
        val t = text?.replace(" ", "")?.replace(",", "") ?: return null
        if (t.isEmpty()) return null

        RANGE.find(t)?.let { m ->
            val a = m.groupValues[1].toFloat()
            val b = m.groupValues[2].toFloat()
            return rangeOf((a + b) / 2f)
        }

        val hours = hoursIn(t)
        if (hours != null) {
            val below = BELOW_WORDS.any { it in t }
            return rangeOf(if (below) hours - 0.5f else hours)
        }
        if (SHORT_SLEEP_WORDS.any { it in t }) return SleepRange.UNDER_4H
        return null
    }

    /** 문장 안 "N시간" (숫자·한글 수사 모두). "반"이 붙으면 0.5시간 더한다 */
    private fun hoursIn(t: String): Float? {
        DIGIT_HOURS.find(t)?.let { m ->
            val half = if (m.groupValues[2].isNotEmpty()) 0.5f else 0f
            return m.groupValues[1].toFloat() + half
        }
        // 긴 말부터 찾는다 ("열두"가 "열"·"두"보다 먼저)
        for ((word, n) in KOREAN_NUMBERS) {
            val i = t.indexOf(word + "시간")
            if (i >= 0) {
                val after = t.substring(i + word.length + 2)
                return n + if (after.startsWith("반")) 0.5f else 0f
            }
        }
        return null
    }

    private fun rangeOf(hours: Float): SleepRange = when {
        hours < 4f -> SleepRange.UNDER_4H
        hours < 6f -> SleepRange.H4_TO_6
        hours < 8f -> SleepRange.H6_TO_8
        else -> SleepRange.OVER_8H
    }

    private val RANGE = Regex("""(\d+(?:\.\d+)?)(?:시간)?(?:~|-|에서|부터)(\d+(?:\.\d+)?)시간""")
    private val DIGIT_HOURS = Regex("""(\d+(?:\.\d+)?)시간(반)?""")

    /** "N시간 미만"으로 읽는 말. "4시간밖에 못 잤어"는 4시간 그대로라 "못잤"만으로는 넣지 않는다 */
    private val BELOW_WORDS = listOf("미만", "안됐", "안되", "이하", "도못", "도안", "안돼")

    /** 숫자 없이 잠을 거의 못 잤다는 말 */
    private val SHORT_SLEEP_WORDS = listOf("한숨도", "거의못", "거의안", "얼마못", "얼마안", "못잤", "안잤")

    private val KOREAN_NUMBERS: List<Pair<String, Int>> = listOf(
        "스물네" to 24, "스물셋" to 23, "스물세" to 23, "스물두" to 22, "스물한" to 21, "스무" to 20,
        "열아홉" to 19, "열여덟" to 18, "열일곱" to 17, "열여섯" to 16, "열다섯" to 15,
        "열네" to 14, "열세" to 13, "열두" to 12, "열한" to 11, "열" to 10,
        "아홉" to 9, "여덟" to 8, "일곱" to 7, "여섯" to 6, "다섯" to 5,
        "네" to 4, "세" to 3, "두" to 2, "한" to 1,
        // 한자어 수사 ("삼 시간"처럼 말하는 경우)
        "십" to 10, "구" to 9, "팔" to 8, "칠" to 7, "육" to 6, "오" to 5, "사" to 4, "삼" to 3, "이" to 2, "일" to 1,
    )
}
