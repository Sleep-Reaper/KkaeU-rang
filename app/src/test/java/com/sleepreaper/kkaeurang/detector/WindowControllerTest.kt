package com.sleepreaper.kkaeurang.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WindowControllerTest {

    private lateinit var controller: WindowController
    private var now = 0L

    @Before
    fun setUp() {
        controller = WindowController()
        now = 1_000_000L
    }

    /** 1초 지나고 한 번 판단 */
    private fun tick(
        score: Int,
        level: Level = levelOf(score),
        speed: Float = 80f,
        park: Boolean = false,
        rain: Boolean = false,
    ): WindowController.Decision {
        now += 1_000L
        return controller.update(WindowController.Input(now, score, level, speed, park, rain))
    }

    /**
     * 같은 상태로 [seconds]번(1초 간격) 판단하고 마지막 결과를 돌려준다.
     * 점수가 낮아진 첫 틱이 0초라서, hold(30) 마지막 틱은 29초째 → 아직 바뀌지 않고, 그다음 틱(30초째)에 바뀐다.
     */
    private fun hold(seconds: Int, score: Int, speed: Float = 80f, rain: Boolean = false): WindowController.Decision {
        var d = tick(score, speed = speed, rain = rain)
        repeat(seconds - 1) { d = tick(score, speed = speed, rain = rain) }
        return d
    }

    private fun levelOf(score: Int) = when {
        score >= 80 -> Level.DANGER
        score >= 70 -> Level.WARNING
        score >= 40 -> Level.CAUTION
        else -> Level.NORMAL
    }

    // ── 열기 ──

    @Test
    fun `졸음 79점까지는 열지 않는다`() {
        val d = tick(79)
        assertEquals(0, d.windowPercent)
        assertFalse(d.coolAir)
    }

    @Test
    fun `졸음 80점이면 바로 30% 열고 찬바람을 켠다`() {
        val d = tick(80)
        assertEquals(30, d.windowPercent)
        assertTrue(d.coolAir)
    }

    @Test
    fun `졸음 90점이면 바로 60% 연다`() {
        assertEquals(60, tick(90).windowPercent)
    }

    @Test
    fun `30%에서 점수가 더 오르면 기다리지 않고 60%로 더 연다`() {
        tick(85)
        assertEquals(60, tick(92).windowPercent)
    }

    @Test
    fun `무응답으로 위험 단계가 되면 점수가 낮아도 30% 연다`() {
        assertEquals(30, tick(50, level = Level.DANGER).windowPercent)
    }

    // ── 줄이기·닫기 ──

    @Test
    fun `60%에서 점수가 80점대로 내려가면 30초 뒤에 30%로 줄인다`() {
        tick(95)
        assertEquals(60, hold(30, 85).windowPercent)
        assertEquals(30, tick(85).windowPercent)
    }

    @Test
    fun `한 번 열렸으면 70점대에서는 닫지 않고 30%를 유지한다`() {
        tick(85)
        assertEquals(30, hold(120, 75).windowPercent)
    }

    @Test
    fun `70점 아래가 30초 이어지면 닫고 찬바람도 끈다`() {
        tick(85)
        val before = hold(30, 60)
        assertEquals(30, before.windowPercent)
        val after = tick(60)
        assertEquals(0, after.windowPercent)
        assertFalse(after.coolAir)
    }

    @Test
    fun `닫기 대기 중에 점수가 다시 오르면 대기가 처음부터 다시 시작된다`() {
        tick(85)
        hold(20, 60)
        tick(80)                                   // 다시 열 조건 → 대기 초기화
        assertEquals(30, hold(30, 60).windowPercent)
        assertEquals(0, tick(60).windowPercent)
    }

    @Test
    fun `80점 경계를 오르내려도 창문이 열렸다 닫혔다 하지 않는다`() {
        tick(80)
        repeat(60) { i ->
            val d = tick(if (i % 2 == 0) 79 else 80)
            assertEquals(30, d.windowPercent)
        }
    }

    @Test
    fun `60%에서 70점 아래로 바로 떨어지면 30초 뒤에 한 번에 닫는다`() {
        tick(95)
        assertEquals(60, hold(30, 50).windowPercent)
        assertEquals(0, tick(50).windowPercent)
    }

    // ── P단 ──

    @Test
    fun `P단이 되면 바로 닫고 찬바람을 끈다`() {
        tick(95)
        val d = tick(95, park = true)
        assertEquals(0, d.windowPercent)
        assertFalse(d.coolAir)
    }

    @Test
    fun `P단에서 다시 출발하면 처음부터 판단한다`() {
        tick(85)
        tick(85, park = true)
        assertEquals(0, tick(75).windowPercent)    // 이전에 열렸던 기록이 남아 있지 않다
    }

    // ── 고속 ──

    @Test
    fun `100km_h 이상이면 최대 20%까지만 연다`() {
        val d = tick(95, speed = 110f)
        assertEquals(20, d.windowPercent)
        assertTrue(d.limitedBySpeed)
        assertTrue(d.coolAir)
    }

    @Test
    fun `속도가 줄면 원래 열림 정도로 돌아간다`() {
        tick(95, speed = 110f)
        val d = tick(95, speed = 90f)
        assertEquals(60, d.windowPercent)
        assertFalse(d.limitedBySpeed)
    }

    @Test
    fun `99km_h는 제한하지 않는다`() {
        assertEquals(60, tick(95, speed = 99f).windowPercent)
    }

    // ── 비 ──

    @Test
    fun `비가 오면 창문은 닫아 두고 찬바람만 켠다`() {
        val d = tick(95, rain = true)
        assertEquals(0, d.windowPercent)
        assertTrue(d.coolAir)
        assertTrue(d.blockedByRain)
    }

    @Test
    fun `비가 그치면 바로 연다`() {
        tick(95, rain = true)
        assertEquals(60, tick(95).windowPercent)
    }

    @Test
    fun `열 조건이 아니면 비가 와도 찬바람을 켜지 않는다`() {
        val d = tick(50, rain = true)
        assertFalse(d.coolAir)
        assertFalse(d.blockedByRain)
    }
}
