package com.sleepreaper.kkaeurang.detector

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DrowsinessCheckerTest {

    // ── 가짜 신호원과 가짜 모듈 ──
    private class FakeSource : SignalSource {
        var speed = 80f
        var steer = 0f
        override fun start() = Unit
        override fun stop() = Unit
        override fun sample(now: Long) = Sample(now, speed, steer, false, false, false)
    }

    private class FakeVoice : DriverVoice {
        var askCount = 0
        var stopAllCount = 0
        private var pending: ((VoiceAnswer) -> Unit)? = null
        override fun ask(question: String, onResult: (VoiceAnswer) -> Unit) {
            askCount++
            pending = onResult
        }
        override fun stopAll() { stopAllCount++ }
        fun respond(answer: VoiceAnswer) {
            val cb = pending!!
            pending = null            // 콜백 안에서 재질문하면 새 pending이 들어온다
            cb(answer)
        }
    }

    private class FakeAlarm : AlarmModule {
        var starts = 0
        var stops = 0
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }

    private class FakeWindow : WindowModule {
        var opens = 0
        override fun open() { opens++ }
    }

    private class FakeNavi : NaviModule {
        var calls = 0
        override fun guideToRestArea() { calls++ }
    }

    private lateinit var src: FakeSource
    private lateinit var voice: FakeVoice
    private lateinit var alarm: FakeAlarm
    private lateinit var win: FakeWindow
    private lateinit var navi: FakeNavi
    private lateinit var checker: DrowsinessChecker

    // 졸음 위험 시간대(02~04시, 14~15시)가 아닌 낮 10시 반에서 시작
    private var now = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 12, 10, 30, 0)
    }.timeInMillis

    @Before fun setUp() {
        src = FakeSource()
        voice = FakeVoice()
        alarm = FakeAlarm()
        win = FakeWindow()
        navi = FakeNavi()
        checker = DrowsinessChecker(src, voice, alarm, win, navi, clock = { now }, log = {})
    }

    private val result get() = checker.result.value

    /** 핸들을 [steer]로 두고 1초씩 [n]번 진행 */
    private fun tickN(n: Int, steer: Float = 0f) {
        repeat(n) {
            src.steer = steer
            checker.tick(now)
            now += 1000
        }
    }

    /** 핸들을 조금씩 움직이며 진행 → 어떤 이벤트 규칙도 발생하지 않는다 */
    private fun tickVarying(n: Int) {
        repeat(n) { i ->
            src.steer = (i % 5) * 3f
            checker.tick(now)
            now += 1000
        }
    }

    /** 12초 고정(①) 후 급보정(②): 이벤트 점수 약 +40 → CAUTION */
    private fun cycle1() { tickN(12); tickN(1, 20f) }

    /** 급보정 직후 이어서 ①+② 반복: 이벤트 점수 약 +40 */
    private fun nextCycle() { tickN(10); tickN(1); tickN(1, 20f) }

    // ── 점수·단계 ──

    @Test fun normalDriving_staysNormal() {
        tickVarying(30)
        assertEquals(Level.NORMAL, result.level)
        assertEquals(0, result.score)
        assertEquals(0, alarm.starts)
        assertEquals(0, voice.askCount)
    }

    @Test fun steeringFreeze_addsPointsButStaysNormal() {
        tickN(12)
        assertEquals(Level.NORMAL, result.level)
        assertTrue("score=${result.score}", result.score in 10..15)
        assertTrue(result.reasons.contains("핸들 조작 없음 10초 이상"))
    }

    @Test fun freezeThenCorrection_reachesCautionAndAsksOnce() {
        cycle1()
        assertEquals(Level.CAUTION, result.level)
        assertEquals(1, voice.askCount)
        tickN(1)                       // 계속 CAUTION이어도 다시 묻지 않는다
        assertEquals(1, voice.askCount)
    }

    @Test fun warning_startsAlarmOnly() {
        cycle1(); nextCycle()
        assertEquals(Level.WARNING, result.level)
        assertEquals(1, alarm.starts)
        assertEquals(0, win.opens)
        assertEquals(0, navi.calls)
    }

    @Test fun danger_startsAlarmWindowAndNaviOnce() {
        cycle1(); nextCycle(); nextCycle()
        assertEquals(Level.DANGER, result.level)
        assertEquals(1, alarm.starts)          // WARNING→DANGER에서 알람을 또 시작하지 않는다
        assertEquals(1, win.opens)
        assertEquals(1, navi.calls)
    }

    @Test fun alarmStops_whenScoreFallsBelowWarning() {
        cycle1(); nextCycle(); nextCycle()
        tickVarying(1000)                      // 점수가 서서히 감소
        assertTrue("level=${result.level}", result.level < Level.WARNING)
        assertEquals(1, alarm.stops)
        assertEquals(1, win.opens)             // 창문·내비는 다시 실행되지 않는다
    }

    // ── 운전자 응답 ──

    @Test fun admitted_raisesScoreToWarning() {
        cycle1()
        voice.respond(VoiceAnswer("졸려요", false, 1500L))
        tickVarying(1)
        assertEquals(Level.WARNING, result.level)
        assertEquals(1, alarm.starts)
    }

    @Test fun noAnswer_forcesWarningForThreeMinutes() {
        cycle1()
        voice.respond(VoiceAnswer(null, true, null))
        tickVarying(1)
        assertEquals(Level.WARNING, result.level)
        assertEquals(1, alarm.starts)
        assertTrue(result.reasons.contains("운전자 응답 없음"))

        tickVarying(200)                       // 3분(180초)이 지나면 원래 단계로
        assertTrue("level=${result.level}", result.level < Level.WARNING)
        assertEquals(1, alarm.stops)
    }

    @Test fun unclear_retriesOnlyOnce() {
        cycle1()
        voice.respond(VoiceAnswer("음 글쎄요", false, 1000L))
        assertEquals(2, voice.askCount)
        voice.respond(VoiceAnswer("음 글쎄요", false, 1000L))
        assertEquals(2, voice.askCount)
    }

    @Test fun error_doesNotPenalizeOrRetry() {
        cycle1()
        voice.respond(VoiceAnswer(null, false, null))
        tickVarying(1)
        assertEquals(Level.CAUTION, result.level)
        assertEquals(1, voice.askCount)
        assertEquals(0, alarm.starts)
    }

    @Test fun stop_cancelsVoiceAndAlarm() {
        checker.stop()
        assertEquals(1, voice.stopAllCount)
        assertEquals(1, alarm.stops)
    }
}
