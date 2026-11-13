package com.sleepreaper.kkaeurang.detector

import com.sleepreaper.kkaeurang.integration.SimulatedCabinControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinActuatorTest {

    private var now = 1_000_000L
    private lateinit var cabin: SimulatedCabinControl
    private lateinit var actuator: CabinActuator
    private val events = mutableListOf<CabinActuator.Event>()

    private fun setUp(initialWindow: Int = 0) {
        cabin = SimulatedCabinControl(initialWindowPercent = initialWindow).also { it.start() }
        actuator = CabinActuator(cabin, clock = { now })
        actuator.onEvent = { events += it }
    }

    private fun decide(window: Int, coolAir: Boolean = window > 0) =
        WindowController.Decision(window, coolAir, blockedByRain = false, limitedBySpeed = false)

    /** 1초 지나고 판단 결과를 실행 */
    private fun apply(window: Int, coolAir: Boolean = window > 0, park: Boolean = false) {
        now += 1_000L
        actuator.apply(decide(window, coolAir), park)
    }

    /** 창문이 움직이는 시간(명령 후 10초)이 지나게 한다 */
    private fun waitForMove() {
        now += CabinActuator.MOVE_TIMEOUT_MS
    }

    // ── 열고 원래대로 되돌리기 ──

    @Test
    fun `닫혀 있던 창문을 열고, 끝나면 다시 닫는다`() {
        setUp(initialWindow = 0)
        apply(30)
        assertEquals(30, cabin.windowOpenPercent)
        assertTrue(cabin.coolAirOn)

        apply(0)
        assertEquals(0, cabin.windowOpenPercent)
        assertFalse(cabin.coolAirOn)
    }

    @Test
    fun `조금 열려 있던 창문은 닫을 때 원래 위치로 되돌린다`() {
        setUp(initialWindow = 10)
        apply(60)
        assertEquals(60, cabin.windowOpenPercent)
        apply(0)
        assertEquals(10, cabin.windowOpenPercent)
    }

    @Test
    fun `원래 더 열려 있었으면 그보다 닫지 않는다`() {
        setUp(initialWindow = 50)
        apply(30)
        assertEquals(50, cabin.windowOpenPercent)
        apply(0)
        assertEquals(50, cabin.windowOpenPercent)
    }

    @Test
    fun `60퍼센트에서 30퍼센트로 줄여도 처음 기억한 원래 위치로 되돌린다`() {
        setUp(initialWindow = 5)
        apply(60)
        apply(30)
        assertEquals(30, cabin.windowOpenPercent)
        apply(0)
        assertEquals(5, cabin.windowOpenPercent)
    }

    @Test
    fun `되돌린 뒤 다시 열 때는 그때 위치를 새로 기억한다`() {
        setUp(initialWindow = 0)
        apply(30)
        apply(0)
        cabin.simulateDriverMove(20)               // 우리가 손 뗀 뒤 운전자가 연 것은 상관없다
        apply(30)
        apply(0)
        assertEquals(20, cabin.windowOpenPercent)
    }

    /** 차량에 보낸 명령 횟수를 센다 */
    private class CountingCabin(private val inner: CabinControl) : CabinControl by inner {
        var windowCalls = 0
        var coolAirCalls = 0
        override fun setWindowOpenPercent(percent: Int, onResult: (success: Boolean) -> Unit) {
            windowCalls++
            inner.setWindowOpenPercent(percent, onResult)
        }
        override fun setCoolAir(on: Boolean, onResult: (success: Boolean) -> Unit) {
            coolAirCalls++
            inner.setCoolAir(on, onResult)
        }
    }

    @Test
    fun `판단이 그대로면 매초 같은 명령을 다시 보내지 않는다`() {
        val counting = CountingCabin(SimulatedCabinControl().also { it.start() })
        val actuator = CabinActuator(counting, clock = { now })
        repeat(10) {
            now += 1_000L
            actuator.apply(decide(30), isPark = false)
        }
        assertEquals(1, counting.windowCalls)
        assertEquals(1, counting.coolAirCalls)
    }

    // ── 비 ──

    @Test
    fun `열기 전부터 비가 오면 창문은 건드리지 않고 찬바람만 켠다`() {
        setUp(initialWindow = 0)
        apply(0, coolAir = true)
        assertEquals(0, cabin.windowOpenPercent)
        assertTrue(cabin.coolAirOn)
    }

    @Test
    fun `열어 둔 상태에서 비가 오면 원래대로 닫고 찬바람은 유지한다`() {
        setUp(initialWindow = 0)
        apply(60)
        apply(0, coolAir = true)
        assertEquals(0, cabin.windowOpenPercent)
        assertTrue(cabin.coolAirOn)
    }

    // ── 운전자 우선 ──

    @Test
    fun `우리가 연 뒤 운전자가 창문을 움직이면 이번 주행 동안 창문은 손대지 않는다`() {
        setUp()
        apply(30)
        waitForMove()
        cabin.simulateDriverMove(0)                // 운전자가 닫음
        assertTrue(actuator.driverOverride)
        assertEquals(listOf(CabinActuator.Event.DRIVER_OVERRIDE), events)

        apply(60)                                  // 졸음이 더 심해져도
        assertEquals(0, cabin.windowOpenPercent)   // 창문은 운전자가 둔 그대로
        assertTrue(cabin.coolAirOn)                // 찬바람은 계속 판단한다
    }

    @Test
    fun `자동 조절을 멈춘 뒤 끝날 때 운전자가 둔 창문을 되돌리지 않는다`() {
        setUp(initialWindow = 0)
        apply(30)
        waitForMove()
        cabin.simulateDriverMove(80)
        apply(0)
        assertEquals(80, cabin.windowOpenPercent)
        assertFalse(cabin.coolAirOn)
    }

    /** 실제 차처럼 명령을 받아도 바로 도착하지 않고, [report]로 중간 위치를 하나씩 알려 주는 창문 */
    private class SlowCabin : CabinControl {
        override var windowOpenPercent: Int? = 0
        override val isRaining = false
        override var onWindowChanged: ((percent: Int) -> Unit)? = null
        override fun start() = Unit
        override fun stop() = Unit
        override fun setWindowOpenPercent(percent: Int, onResult: (success: Boolean) -> Unit) = onResult(true)
        override fun setCoolAir(on: Boolean, onResult: (success: Boolean) -> Unit) = onResult(true)
        fun report(percent: Int) {
            windowOpenPercent = percent
            onWindowChanged?.invoke(percent)
        }
    }

    @Test
    fun `창문이 움직이는 중에 들어오는 중간 위치는 운전자 조작으로 보지 않는다`() {
        val slow = SlowCabin()
        val actuator = CabinActuator(slow, clock = { now })
        actuator.apply(decide(60), isPark = false)
        now += 2_000L
        slow.report(20)                            // 명령 직후 10초 안: 천천히 올라가는 중간 위치
        now += 2_000L
        slow.report(45)
        now += 2_000L
        slow.report(60)                            // 도착
        assertFalse(actuator.driverOverride)

        now += 1_000L
        slow.report(10)                            // 도착한 뒤의 변화는 운전자 조작
        assertTrue(actuator.driverOverride)
    }

    @Test
    fun `5퍼센트 이내 차이는 센서 오차로 본다`() {
        setUp()
        apply(30)
        waitForMove()
        cabin.simulateDriverMove(34)
        assertFalse(actuator.driverOverride)
    }

    @Test
    fun `우리가 움직인 적 없으면 운전자가 창문을 움직여도 자동 조절은 그대로다`() {
        setUp()
        cabin.simulateDriverMove(40)
        assertFalse(actuator.driverOverride)
    }

    @Test
    fun `P단이 되면 운전자 우선을 풀고 다음 주행에서 다시 조절한다`() {
        setUp()
        apply(30)
        waitForMove()
        cabin.simulateDriverMove(0)
        apply(0, park = true)
        assertFalse(actuator.driverOverride)

        apply(30)
        assertEquals(30, cabin.windowOpenPercent)
    }

    // ── 실패 ──

    @Test
    fun `창문 명령이 실패하면 알리고 같은 목표로는 매초 다시 시도하지 않는다`() {
        setUp()
        cabin.failWindow = true
        repeat(5) { apply(30) }
        assertEquals(listOf(CabinActuator.Event.WINDOW_FAILED), events)
        assertEquals(0, cabin.windowOpenPercent)
    }

    @Test
    fun `실패한 뒤 목표가 바뀌면 다시 시도한다`() {
        setUp()
        cabin.failWindow = true
        apply(30)
        cabin.failWindow = false
        apply(60)
        assertEquals(60, cabin.windowOpenPercent)
    }

    @Test
    fun `찬바람 명령이 실패하면 알린다`() {
        setUp()
        cabin.failCoolAir = true
        apply(30)
        assertEquals(listOf(CabinActuator.Event.COOL_AIR_FAILED), events)
        assertEquals(30, cabin.windowOpenPercent)  // 창문은 따로 동작한다
    }
}
