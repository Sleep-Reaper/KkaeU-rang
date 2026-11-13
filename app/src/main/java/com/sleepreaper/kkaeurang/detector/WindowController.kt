package com.sleepreaper.kkaeurang.detector

/**
 * 졸음 대응 창문·찬바람 판단. 매초 [update]에 엔진 결과와 차량 상태를 넣으면 "지금 창문을 몇 % 열고 찬바람을 켤지"를 돌려준다.
 * 실제로 움직이는 일은 하지 않는다(그건 [CabinControl] 몫). 순수 Kotlin이라 단위 테스트로 확인한다.
 *
 * 점수는 엔진의 **졸음 점수**(높을수록 위험)다. 화면의 안전 점수 = 100 − 졸음 점수.
 *
 * ## 규칙
 * 1. 열기: 졸음 [OPEN_SCORE](80) 이상 → [OPEN_PERCENT](30%), [WIDE_SCORE](90) 이상 → [WIDE_PERCENT](60%)
 *    - 무응답으로 엔진이 DANGER로 올려 둔 동안에는 점수가 낮아도 30%로 본다
 *    - 더 여는 건 바로 한다
 * 2. 줄이기·닫기: 목표가 지금보다 낮은 상태가 [LOWER_HOLD_MS](30초) 이어져야 줄인다 (경계에서 열고 닫기 반복 방지)
 *    - 한 번 열렸으면 졸음 [CLOSE_SCORE](70) 아래로 내려가기 전까지는 닫지 않고 30%를 유지한다
 * 3. P단 정차: 바로 닫고 찬바람도 끈다 (엔진도 이때 경고를 해제한다)
 * 4. 고속: [HIGH_SPEED_KMH](100km/h) 이상이면 최대 [HIGH_SPEED_MAX_PERCENT](20%)까지만 연다
 * 5. 비: 창문은 열지 않고 찬바람만 켠다
 *
 * 고속·비 조건은 **내보내는 값에만** 적용한다. 내부 단계는 그대로라, 비가 그치거나 속도가 줄면 다음 틱에 바로 원래만큼 열린다.
 */
class WindowController {

    /** 매초 넣는 입력 */
    data class Input(
        val now: Long,
        val score: Int,          // 졸음 점수 0~100 (DrowsinessResult.score)
        val level: Level,        // DrowsinessResult.level (무응답으로 올라간 단계 포함)
        val speedKmh: Float,
        val isPark: Boolean,
        val isRaining: Boolean,
    )

    /** 판단 결과. 같은 값이 매초 반복해서 나올 수 있으니, 실행하는 쪽은 바뀐 경우에만 차량에 명령한다. */
    data class Decision(
        val windowPercent: Int,          // 운전석 창문 목표 열림 정도 0~100
        val coolAir: Boolean,            // 찬바람 켜기
        val blockedByRain: Boolean,      // 열어야 하는데 비 때문에 닫아 둠
        val limitedBySpeed: Boolean,     // 고속이라 덜 열었음
    ) {
        companion object {
            val CLOSED = Decision(0, coolAir = false, blockedByRain = false, limitedBySpeed = false)
        }
    }

    /** 비·고속 제한을 적용하기 전의 목표 열림 정도 */
    var stagePercent = 0
        private set

    private var lowerSince: Long? = null     // 목표가 지금보다 낮아지기 시작한 시각

    fun update(input: Input): Decision {
        if (input.isPark) {
            reset()
            return Decision.CLOSED
        }

        val wanted = wantedPercent(input)
        when {
            wanted > stagePercent -> {
                stagePercent = wanted
                lowerSince = null
            }
            wanted < stagePercent -> {
                val since = lowerSince ?: input.now.also { lowerSince = it }
                if (input.now - since >= LOWER_HOLD_MS) {
                    stagePercent = wanted
                    lowerSince = null
                }
            }
            else -> lowerSince = null
        }

        return output(input)
    }

    /** 모든 상태를 처음(닫힘)으로 되돌린다 */
    fun reset() {
        stagePercent = 0
        lowerSince = null
    }

    /** 지금 점수만 보면 원하는 열림 정도 (줄이기 대기 시간 적용 전) */
    private fun wantedPercent(input: Input): Int = when {
        input.score >= WIDE_SCORE -> WIDE_PERCENT
        input.score >= OPEN_SCORE -> OPEN_PERCENT
        input.level == Level.DANGER -> OPEN_PERCENT                       // 무응답으로 올라간 위험 단계
        input.score >= CLOSE_SCORE && stagePercent > 0 -> OPEN_PERCENT    // 이미 열렸으면 70 아래로 내려가야 닫는다
        else -> 0
    }

    private fun output(input: Input): Decision {
        if (stagePercent == 0) return Decision.CLOSED
        if (input.isRaining) {
            return Decision(0, coolAir = true, blockedByRain = true, limitedBySpeed = false)
        }
        val limited = input.speedKmh >= HIGH_SPEED_KMH && stagePercent > HIGH_SPEED_MAX_PERCENT
        val percent = if (limited) HIGH_SPEED_MAX_PERCENT else stagePercent
        return Decision(percent, coolAir = true, blockedByRain = false, limitedBySpeed = limited)
    }

    companion object {
        const val OPEN_SCORE = 80
        const val WIDE_SCORE = 90
        const val CLOSE_SCORE = 70

        const val OPEN_PERCENT = 30
        const val WIDE_PERCENT = 60

        const val LOWER_HOLD_MS = 30_000L

        const val HIGH_SPEED_KMH = 100f
        const val HIGH_SPEED_MAX_PERCENT = 20
    }
}
