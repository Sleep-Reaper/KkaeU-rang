package com.sleepreaper.kkaeurang.integration

import com.sleepreaper.kkaeurang.detector.AlarmModule
import com.sleepreaper.kkaeurang.detector.DriverVoice
import com.sleepreaper.kkaeurang.detector.DrowsinessChecker
import com.sleepreaper.kkaeurang.detector.NaviModule
import com.sleepreaper.kkaeurang.detector.VoiceAnswer
import com.sleepreaper.kkaeurang.detector.WindowModule
import com.sleepreaper.kkaeurang.ui.debug.DriveScenario
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 디버그 패널의 주행 시나리오가 실제 판단 엔진 규칙을 정확히 한 번씩 건드리는지 확인 */
class SimulatedSignalSourceTest {

    private object NoVoice : DriverVoice {
        override fun ask(question: String, onResult: (VoiceAnswer) -> Unit) = Unit
        override fun stopAll() = Unit
    }
    private object NoAlarm : AlarmModule {
        override fun start() = Unit
        override fun stop() = Unit
    }
    private object NoWindow : WindowModule { override fun open() = Unit }
    private object NoNavi : NaviModule { override fun guideToRestArea() = Unit }

    // 위험 시간대가 아닌 오전 10시 반
    private var now = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 12, 10, 30, 0)
    }.timeInMillis

    private val sim = SimulatedSignalSource()
    private val checker = DrowsinessChecker(sim, NoVoice, NoAlarm, NoWindow, NoNavi, clock = { now }, log = {})

    private fun tick(n: Int) = repeat(n) {
        now += 1000
        checker.tick(now)
    }

    private fun scoreLogs() = checker.logs.value.map { it.message }.filter { it.endsWith("점)") }

    @Test
    fun 평소_주행은_어떤_규칙에도_걸리지_않음() {
        tick(120)
        assertEquals(emptyList<String>(), scoreLogs())
        assertEquals(100, checker.result.value.score)   // no penalty -> safety score 100
    }

    @Test
    fun 핸들_고정_시나리오는_규칙1만_한번() {
        tick(5)
        sim.run(DriveScenario.STEERING_FROZEN)
        tick(30)
        assertEquals(listOf("핸들 조작 없음 10초 이상 (-15점)"), scoreLogs())
    }

    @Test
    fun 고정후_급조작_시나리오는_안전_60점으로_주의_단계() {
        tick(5)
        sim.run(DriveScenario.FROZEN_THEN_JERK)
        tick(15)
        assertEquals(
            listOf("핸들 조작 없음 10초 이상 (-15점)", "핸들 고정 후 급조작 (-25점)"),
            scoreLogs(),
        )
        assertTrue(checker.result.value.score <= 61)   // 회복분(1초 0.05점) 때문에 60 바로 위일 수 있음
    }

    @Test
    fun 쏠림_시나리오는_규칙3만_한번() {
        tick(5)
        sim.run(DriveScenario.DRIFT)
        tick(10)
        assertEquals(listOf("방향지시등 없이 한쪽으로 쏠림 (-15점)"), scoreLogs())
    }

    @Test
    fun 깜빡이를_켜면_쏠림이_걸리지_않음() {
        tick(5)
        sim.turnSignalOn = true
        sim.run(DriveScenario.DRIFT)
        tick(10)
        assertEquals(emptyList<String>(), scoreLogs())
    }

    @Test
    fun 속도_불안정_시나리오는_규칙4만_한번() {
        tick(5)
        sim.run(DriveScenario.UNSTABLE_SPEED)
        tick(70)
        assertEquals(listOf("속도 유지 불안정 (-10점)"), scoreLogs())
    }

    @Test
    fun 디버그_감점_조절은_0과_100_사이로_반영() {
        tick(2)
        checker.debugAdjustPenalty(50f)                 // 50 감점 → 안전 점수 50
        tick(1)
        assertEquals(50, checker.result.value.score)
        checker.debugAdjustPenalty(-100f)               // 감점 모두 제거 → 100
        tick(1)
        assertEquals(100, checker.result.value.score)
    }

    @Test
    fun P단이면_정지_샘플() {
        sim.setParked(true)
        tick(3)
        assertTrue(checker.sample.value.isPark)
        assertEquals(0f, checker.sample.value.speedKmh)
    }
}
