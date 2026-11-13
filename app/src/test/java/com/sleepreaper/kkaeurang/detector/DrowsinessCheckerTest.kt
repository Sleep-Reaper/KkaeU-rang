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
        var park = false
        override fun start() = Unit
        override fun stop() = Unit
        override fun sample(now: Long) = Sample(now, speed, steer, false, false, park)
    }

    private class FakeVoice : DriverVoice {
        var askCount = 0
        var stopAllCount = 0
        val said = mutableListOf<String>()
        private var pending: ((VoiceAnswer) -> Unit)? = null
        override fun ask(question: String, onResult: (VoiceAnswer) -> Unit) {
            askCount++
            pending = onResult
        }
        override fun stopAll() { stopAllCount++ }
        override fun say(text: String) { said += text }
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
        var succeed = true
        override fun open() { opens++ }
        override fun open(onResult: (Boolean) -> Unit) { opens++; onResult(succeed) }
    }

    private class FakeNavi : NaviModule {
        var calls = 0
        var succeed = true
        override fun guideToRestArea() { calls++ }
        override fun guideToRestArea(onResult: (Boolean) -> Unit) { calls++; onResult(succeed) }
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

    // ── UI에 제공하는 값 ──

    private val logMessages get() = checker.logs.value.map { it.message }

    @Test fun sample_isPublishedEveryTick() {
        src.speed = 77f
        src.steer = 12f
        tickN(1, 12f)
        assertEquals(77f, checker.sample.value.speedKmh, 0.001f)
        assertEquals(12f, checker.sample.value.steeringDeg, 0.001f)

        src.speed = 55f
        tickN(1, -3f)
        assertEquals(55f, checker.sample.value.speedKmh, 0.001f)
        assertEquals(-3f, checker.sample.value.steeringDeg, 0.001f)
    }

    @Test fun result_changesEveryTick_evenWhenScoreIsSame() {
        tickVarying(1)
        val first = result.timestamp
        tickVarying(1)
        assertTrue(result.timestamp > first)   // StateFlow가 같은 값으로 무시하지 않는다
    }

    @Test fun logs_recordEventsLevelChangeAndQuestion() {
        tickVarying(30)
        assertTrue("사건이 없으면 로그도 비어 있어야 한다", checker.logs.value.isEmpty())

        cycle1()
        assertTrue(logMessages.any { it == "핸들 조작 없음 10초 이상 (+15점)" })
        assertTrue(logMessages.any { it == "핸들 고정 후 급조작 (+25점)" })
        assertTrue(logMessages.any { it.startsWith("단계 변경: 정상 → 주의") })
        assertTrue(logMessages.any { it == "운전자에게 질문" })
    }

    @Test fun logs_recordAlarmWindowAndNaviCalls() {
        cycle1(); nextCycle(); nextCycle()
        assertTrue(logMessages.contains("알람 시작"))
        assertTrue(logMessages.contains("창문 열기, 졸음쉼터 안내 요청"))
    }

    @Test fun logs_keepAnswerLabelButNotSpokenText() {
        cycle1()
        voice.respond(VoiceAnswer("졸려요 제발", false, 1500L))
        tickVarying(1)
        assertTrue(logMessages.contains("운전자 응답: 졸림 인정"))
        assertTrue("원문이 로그에 들어가면 안 된다", logMessages.none { it.contains("제발") })
    }

    @Test fun logs_areCappedAt100_keepingNewest() {
        cycle1()
        repeat(60) { nextCycle() }
        assertEquals(100, checker.logs.value.size)
        val times = checker.logs.value.map { it.time }
        assertEquals(times.sorted(), times)    // 오래된 것이 앞
    }

    // ── 정차(P단) 해제 ──

    @Test fun parkedAtWarning_stopsAlarm_announces_andResetsScore() {
        cycle1(); nextCycle()
        assertEquals(Level.WARNING, result.level)

        src.park = true
        tickN(4)                               // P단 3초 유지 확인 후 해제

        assertEquals(Level.NORMAL, result.level)
        assertEquals(0, result.score)
        assertTrue(alarm.stops >= 1)
        assertTrue(voice.said.contains("잘 쉬고 출발하세요."))
        assertTrue(logMessages.contains("정차(P단) → 경고 해제"))
    }

    @Test fun parkedAtWarning_notReleasedBeforeConfirmTime() {
        cycle1(); nextCycle()
        assertEquals(Level.WARNING, result.level)

        src.park = true
        tickN(3)                               // P단 시작 + 2초: 아직 3초가 아니다

        assertEquals(Level.WARNING, result.level)
        assertTrue(voice.said.none { it == "잘 쉬고 출발하세요." })
        assertEquals(0, alarm.stops)
    }

    @Test fun parkFlicker_doesNotRelease() {
        cycle1(); nextCycle()
        assertEquals(Level.WARNING, result.level)

        src.park = true
        tickN(2)                               // 잠깐 P로 읽힘
        src.park = false
        tickN(1)                               // 다시 D단 → 타이머 초기화
        src.park = true
        tickN(2)                               // 다시 P: 처음부터 세야 한다

        assertEquals(Level.WARNING, result.level)
        assertTrue(voice.said.none { it == "잘 쉬고 출발하세요." })
    }

    @Test fun parkedAtWarning_announcesOnlyOnce() {
        cycle1(); nextCycle()
        src.park = true
        tickN(5)
        assertEquals(1, voice.said.count { it == "잘 쉬고 출발하세요." })
    }

    @Test fun parkedAtWarning_clearsForcedNoAnswerLevel() {
        cycle1()
        voice.respond(VoiceAnswer(null, true, null))     // 무응답 → WARNING 3분 강제
        tickVarying(1)
        assertEquals(Level.WARNING, result.level)

        src.park = true
        tickN(4)
        assertEquals(Level.NORMAL, result.level)
    }

    @Test fun parkedAtCaution_doesNotReleaseOrAnnounce() {
        cycle1()
        assertEquals(Level.CAUTION, result.level)
        val before = result.score

        src.park = true
        tickN(5)

        assertTrue(voice.said.isEmpty())
        assertTrue("주의 단계에서는 점수를 초기화하지 않는다 (before=$before, now=${result.score})", result.score >= before - 1)
    }

    // ── DANGER 안내와 창문·내비 실패 대체 ──

    @Test fun danger_announcesByVoice() {
        cycle1(); nextCycle(); nextCycle()
        assertEquals(Level.DANGER, result.level)
        assertEquals(1, voice.said.count { it.contains("졸음쉼터로 안내할게요") })
        assertTrue(voice.said.none { it.contains("열지 못했어요") })
    }

    @Test fun danger_windowFails_fallsBackToVoice() {
        win.succeed = false
        cycle1(); nextCycle(); nextCycle()
        assertEquals(1, win.opens)
        assertTrue(voice.said.any { it.contains("창문을 열지 못했어요") })
        assertTrue(logMessages.contains("창문 열기 실패 → 음성 안내로 대체"))
    }

    @Test fun danger_naviFails_fallsBackToVoice() {
        navi.succeed = false
        cycle1(); nextCycle(); nextCycle()
        assertEquals(1, navi.calls)
        assertTrue(voice.said.any { it.contains("졸음쉼터 안내에 실패했어요") })
    }

    @Test fun stop_cancelsVoiceAndAlarm() {
        checker.stop()
        assertEquals(1, voice.stopAllCount)
        assertEquals(1, alarm.stops)
    }
}
