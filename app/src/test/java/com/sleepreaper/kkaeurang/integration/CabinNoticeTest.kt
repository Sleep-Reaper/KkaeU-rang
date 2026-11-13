package com.sleepreaper.kkaeurang.integration

import com.sleepreaper.kkaeurang.detector.WindowController.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CabinNoticeTest {

    private val closed = Decision.CLOSED
    private fun open(percent: Int) = Decision(percent, coolAir = true, blockedByRain = false, limitedBySpeed = false)
    private val rain = Decision(0, coolAir = true, blockedByRain = true, limitedBySpeed = false)

    private fun notice(
        prev: Decision,
        now: Decision,
        driverOverride: Boolean = false,
        windowFailed: Boolean = false,
        isPark: Boolean = false,
    ) = cabinNoticeFor(prev, now, driverOverride, windowFailed, isPark)

    @Test
    fun `닫혀 있다가 열면 열었다고 알린다`() {
        assertEquals(CabinNotice.OPENED, notice(closed, open(30)))
    }

    @Test
    fun `이미 열린 상태에서 더 열거나 줄일 때는 다시 알리지 않는다`() {
        assertNull(notice(open(30), open(60)))
        assertNull(notice(open(60), open(30)))
    }

    @Test
    fun `고속이라 20퍼센트만 열어도 열었다고 알린다`() {
        val limited = Decision(20, coolAir = true, blockedByRain = false, limitedBySpeed = true)
        assertEquals(CabinNotice.OPENED, notice(closed, limited))
    }

    @Test
    fun `창문 명령이 실패했으면 열었다고 알리지 않는다`() {
        assertNull(notice(closed, open(30), windowFailed = true))
    }

    @Test
    fun `운전자가 직접 조작해 자동 조절을 멈췄으면 열었다고 알리지 않는다`() {
        assertNull(notice(closed, open(30), driverOverride = true))
    }

    @Test
    fun `창문과 찬바람을 모두 끄면 닫았다고 알린다`() {
        assertEquals(CabinNotice.CLOSED, notice(open(30), closed))
    }

    @Test
    fun `비로 찬바람만 켜 두었다가 끌 때도 닫았다고 알린다`() {
        assertEquals(CabinNotice.CLOSED, notice(rain, closed))
    }

    @Test
    fun `P단으로 멈춰서 닫을 때는 알리지 않는다`() {
        assertNull(notice(open(60), closed, isPark = true))
    }

    @Test
    fun `비가 와서 창문 대신 찬바람을 켜면 이유와 함께 알린다`() {
        assertEquals(CabinNotice.BLOCKED_BY_RAIN, notice(closed, rain))
    }

    @Test
    fun `열어 둔 상태에서 비가 오기 시작해도 찬바람으로 바꿨다고 알린다`() {
        assertEquals(CabinNotice.BLOCKED_BY_RAIN, notice(open(30), rain))
    }

    @Test
    fun `비가 계속 오는 동안에는 매초 다시 알리지 않는다`() {
        assertNull(notice(rain, rain))
    }

    @Test
    fun `판단이 그대로면 알리지 않는다`() {
        assertNull(notice(closed, closed))
        assertNull(notice(open(30), open(30)))
    }

    // ───────── 디버그 패널 사건 로그 ─────────

    private fun log(prev: Decision, now: Decision, isPark: Boolean = false) = cabinLogFor(prev, now, isPark)
    private val limited20 = Decision(20, coolAir = true, blockedByRain = false, limitedBySpeed = true)

    @Test
    fun `처음 열 때 열림 정도와 찬바람 켜기를 남긴다`() {
        assertEquals("창문 30% 열기 + 찬바람 켜기", log(closed, open(30)))
    }

    @Test
    fun `더 열거나 줄이면 그 정도를 남긴다`() {
        assertEquals("창문 60%로 넓히기", log(open(30), open(60)))
        assertEquals("창문 30%로 줄이기", log(open(60), open(30)))
    }

    @Test
    fun `고속 제한은 표시해서 남긴다`() {
        assertEquals("창문 20% 열기 (고속이라 제한) + 찬바람 켜기", log(closed, limited20))
        assertEquals("창문 20%로 줄이기 (고속이라 제한)", log(open(60), limited20))
    }

    @Test
    fun `졸음이 가셔 닫으면 남긴다`() {
        assertEquals("졸음이 가셔 창문 닫기", log(open(30), closed))
    }

    @Test
    fun `P단으로 닫으면 P단 때문이라고 남긴다`() {
        assertEquals("P단 → 창문·찬바람 원래대로", log(open(60), closed, isPark = true))
    }

    @Test
    fun `비가 오면 창문 대신 찬바람만 켰다고 남긴다`() {
        assertEquals("비가 와서 창문 대신 찬바람만 켜기", log(closed, rain))
        assertEquals("비가 와서 창문 원래대로, 찬바람만 유지", log(open(30), rain))
    }

    @Test
    fun `비가 그쳐 열면 남긴다`() {
        assertEquals("비가 그쳐 창문 30% 열기", log(rain, open(30)))
    }

    @Test
    fun `판단이 그대로면 로그를 남기지 않는다`() {
        assertNull(log(closed, closed))
        assertNull(log(open(30), open(30)))
        assertNull(log(rain, rain))
    }
}
