package com.sleepreaper.kkaeurang.detector

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 졸음 판단 규칙 ①~⑥. 각 함수는 "최근 샘플 목록"을 받아 조건이 맞는지만 돌려준다.
 * 샘플은 1초 간격이므로 샘플 N장 = N초.
 */
object DrowsinessRules {

    // ── 기준값 (테스트하며 조정) ──
    const val FREEZE_MIN_SPEED = 60f     // ① 이 속도 이상에서만 판단
    const val FREEZE_SECONDS = 10        // ① 고정 지속 시간
    const val FREEZE_RANGE_DEG = 2f      // ① ±1° → 최대-최소 2° 이내
    const val CORRECTION_DEG = 15f       // ② 1초 사이 변화량
    const val DRIFT_DEG = 8f             // ③ 한쪽으로 유지되는 각도
    const val DRIFT_SECONDS = 3          // ③ 유지 시간
    const val SPEED_WINDOW = 60          // ④ 1분
    const val SPEED_MIN = 40f            // ④ 이 속도 이상에서만 판단
    const val SPEED_STD_KMH = 8f         // ④ 표준편차 기준
    const val LONG_DRIVE_MS = 2 * 60 * 60 * 1000L  // ⑤ 2시간

    /** ① 핸들 고정: 최근 10초 동안 고속인데 각도가 거의 안 변함 */
    fun isSteeringFrozen(s: List<Sample>): Boolean {
        if (s.size < FREEZE_SECONDS) return false
        val w = s.takeLast(FREEZE_SECONDS)
        if (w.any { it.speedKmh < FREEZE_MIN_SPEED }) return false
        val range = w.maxOf { it.steeringDeg } - w.minOf { it.steeringDeg }
        return range <= FREEZE_RANGE_DEG
    }

    /** ② 급보정: 직전까지 고정이었는데 방금 1초 사이 크게 꺾음 */
    fun isSuddenCorrection(s: List<Sample>): Boolean {
        if (s.size < FREEZE_SECONDS + 1) return false
        val wasFrozen = isSteeringFrozen(s.dropLast(1))   // 마지막 1장 빼고 본 상태
        val delta = abs(s.last().steeringDeg - s[s.size - 2].steeringDeg)
        return wasFrozen && delta >= CORRECTION_DEG
    }

    /** ③ 깜빡이 없이 한쪽으로 쏠림 */
    fun isDriftWithoutSignal(s: List<Sample>): Boolean {
        if (s.size < DRIFT_SECONDS) return false
        val w = s.takeLast(DRIFT_SECONDS)
        val noSignal = w.none { it.turnSignalOn }
        val allLeft = w.all { it.steeringDeg <= -DRIFT_DEG }
        val allRight = w.all { it.steeringDeg >= DRIFT_DEG }
        val moving = w.all { it.speedKmh >= FREEZE_MIN_SPEED }
        return noSignal && moving && (allLeft || allRight)
    }

    /** ④ 브레이크 없이 속도가 들쭉날쭉 */
    fun isSpeedUnstable(s: List<Sample>): Boolean {
        if (s.size < SPEED_WINDOW) return false
        val w = s.takeLast(SPEED_WINDOW)
        if (w.any { it.brakeOn || it.speedKmh < SPEED_MIN }) return false
        val mean = w.map { it.speedKmh }.average()
        val std = sqrt(w.map { (it.speedKmh - mean) * (it.speedKmh - mean) }.average())
        return std >= SPEED_STD_KMH
    }

    /** ⑤ 연속 주행 2시간 이상 */
    fun isLongDrive(driveStartMs: Long?, now: Long): Boolean =
        driveStartMs != null && now - driveStartMs >= LONG_DRIVE_MS

    /** ⑥ 졸리기 쉬운 시간대 */
    fun isRiskyHour(now: Long): Boolean {
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return h in 2..4 || h in 14..15      // 02:00~04:59, 14:00~15:59
    }
}
