package com.sleepreaper.kkaeurang.integration

import com.sleepreaper.kkaeurang.detector.WindowController

/*
 * 창문·찬바람 판단이 바뀔 때 남길 안내를 정하는 순수 함수들 (Android 의존 없음 → 단위 테스트 대상).
 * - [cabinNoticeFor]: 운전자에게 보여 줄 화면 배너
 * - [cabinLogFor]: 디버그 패널 "엔진 사건 로그" 한 줄
 */

/** 창문·찬바람 판단이 바뀔 때 화면 배너로 알려 줄 안내 */
enum class CabinNotice {
    /** "졸음 감지로 창문을 조금 열었어요" */
    OPENED,

    /** "졸음이 가셔서 창문을 원래대로 닫았어요" */
    CLOSED,

    /** "졸음 감지로 창문을 열려고 했어요" + 이유 "비가 와서 창문 대신 찬바람을 켰어요" */
    BLOCKED_BY_RAIN,
}

/**
 * 직전 판단 [prev]에서 이번 판단 [now]로 바뀔 때 띄울 안내. 바뀐 게 없으면 null.
 *
 * - 비 때문에 창문 대신 찬바람을 켜기 시작하면 [CabinNotice.BLOCKED_BY_RAIN]
 * - 닫혀 있다가 열기 시작하면 [CabinNotice.OPENED]. 운전자가 직접 조작해 자동 조절을 멈췄거나 창문 명령이 실패했으면 띄우지 않는다
 * - 창문·찬바람을 모두 끄면 [CabinNotice.CLOSED]. P단으로 멈춰서 끈 것은 주행 종료라 띄우지 않는다
 *
 * 되돌리기 버튼으로 끈 경우는 이 함수를 거치지 않는다 (운전자가 직접 누른 것이라 따로 알리지 않음).
 */
fun cabinNoticeFor(
    prev: WindowController.Decision,
    now: WindowController.Decision,
    driverOverride: Boolean,
    windowFailed: Boolean,
    isPark: Boolean,
): CabinNotice? = when {
    now.blockedByRain && !prev.blockedByRain && now.coolAir -> CabinNotice.BLOCKED_BY_RAIN
    now.windowPercent > 0 && prev.windowPercent == 0 && !driverOverride && !windowFailed -> CabinNotice.OPENED
    now.windowPercent == 0 && !now.coolAir && (prev.windowPercent > 0 || prev.coolAir) && !isPark -> CabinNotice.CLOSED
    else -> null
}

/**
 * 직전 판단 [prev]에서 이번 판단 [now]로 바뀔 때 디버그 패널 "엔진 사건 로그"에 남길 한 줄. 바뀐 게 없으면 null.
 * 화면 배너([cabinNoticeFor])보다 자세히 남긴다 (넓히기·줄이기, 고속 제한, P단 닫기까지).
 */
fun cabinLogFor(
    prev: WindowController.Decision,
    now: WindowController.Decision,
    isPark: Boolean,
): String? {
    val speedNote = if (now.limitedBySpeed) " (고속이라 제한)" else ""
    val coolAirNote = if (now.coolAir && !prev.coolAir) " + 찬바람 켜기" else ""
    return when {
        now.windowPercent == 0 && !now.coolAir && (prev.windowPercent > 0 || prev.coolAir) ->
            if (isPark) "P단 → 창문·찬바람 원래대로" else "졸음이 가셔 창문 닫기"
        now.blockedByRain && !prev.blockedByRain ->
            if (prev.windowPercent > 0) "비가 와서 창문 원래대로, 찬바람만 유지" else "비가 와서 창문 대신 찬바람만 켜기"
        prev.blockedByRain && !now.blockedByRain && now.windowPercent > 0 ->
            "비가 그쳐 창문 ${now.windowPercent}% 열기$speedNote"
        now.windowPercent > 0 && prev.windowPercent == 0 ->
            "창문 ${now.windowPercent}% 열기$speedNote$coolAirNote"
        now.windowPercent > prev.windowPercent ->
            "창문 ${now.windowPercent}%로 넓히기$speedNote"
        now.windowPercent in 1 until prev.windowPercent ->
            "창문 ${now.windowPercent}%로 줄이기$speedNote"
        else -> null
    }
}
