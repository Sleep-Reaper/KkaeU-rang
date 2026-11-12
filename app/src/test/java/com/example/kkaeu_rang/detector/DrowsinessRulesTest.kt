package com.example.kkaeu_rang.detector

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrowsinessRulesTest {

    private fun sample(
        speed: Float = 80f,
        steer: Float = 0f,
        signal: Boolean = false,
        brake: Boolean = false,
        park: Boolean = false
    ) = Sample(0L, speed, steer, signal, brake, park)

    private fun repeat(n: Int, make: () -> Sample) = List(n) { make() }

    private fun timeAt(hour: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 12, hour, 30, 0)
    }.timeInMillis

    // ── ① 핸들 고정 ──
    @Test fun frozen_true_whenHighSpeedAndNoSteering() {
        assertTrue(DrowsinessRules.isSteeringFrozen(repeat(10) { sample() }))
    }

    @Test fun frozen_false_whenLessThan10Seconds() {
        assertFalse(DrowsinessRules.isSteeringFrozen(repeat(9) { sample() }))
    }

    @Test fun frozen_false_whenSlow() {
        assertFalse(DrowsinessRules.isSteeringFrozen(repeat(10) { sample(speed = 30f) }))
    }

    @Test fun frozen_false_whenSteeringMoves() {
        val s = repeat(9) { sample() } + sample(steer = 3f)
        assertFalse(DrowsinessRules.isSteeringFrozen(s))
    }

    // ── ② 급보정 ──
    @Test fun correction_true_afterFreezeThenBigTurn() {
        val s = repeat(10) { sample() } + sample(steer = 20f)
        assertTrue(DrowsinessRules.isSuddenCorrection(s))
    }

    @Test fun correction_false_withoutFreeze() {
        val s = List(10) { sample(steer = (it % 2) * 5f) } + sample(steer = 25f)
        assertFalse(DrowsinessRules.isSuddenCorrection(s))
    }

    @Test fun correction_false_whenSmallTurn() {
        val s = repeat(10) { sample() } + sample(steer = 5f)
        assertFalse(DrowsinessRules.isSuddenCorrection(s))
    }

    // ── ③ 깜빡이 없이 쏠림 ──
    @Test fun drift_true_whenLeaningWithoutSignal() {
        assertTrue(DrowsinessRules.isDriftWithoutSignal(repeat(3) { sample(steer = 10f) }))
        assertTrue(DrowsinessRules.isDriftWithoutSignal(repeat(3) { sample(steer = -10f) }))
    }

    @Test fun drift_false_whenSignalOn() {
        assertFalse(DrowsinessRules.isDriftWithoutSignal(repeat(3) { sample(steer = 10f, signal = true) }))
    }

    @Test fun drift_false_whenSlow() {
        assertFalse(DrowsinessRules.isDriftWithoutSignal(repeat(3) { sample(steer = 10f, speed = 30f) }))
    }

    // ── ④ 속도 불안정 ──
    @Test fun speedUnstable_true_whenSpeedSwings() {
        val s = List(60) { sample(speed = if (it % 2 == 0) 60f else 90f) }
        assertTrue(DrowsinessRules.isSpeedUnstable(s))
    }

    @Test fun speedUnstable_false_whenSteady() {
        assertFalse(DrowsinessRules.isSpeedUnstable(repeat(60) { sample(speed = 80f) }))
    }

    @Test fun speedUnstable_false_whenBrakeUsed() {
        val s = List(60) { sample(speed = if (it % 2 == 0) 60f else 90f, brake = it == 30) }
        assertFalse(DrowsinessRules.isSpeedUnstable(s))
    }

    // ── ⑤ 장시간 주행 ──
    @Test fun longDrive() {
        val now = 10_000_000_000L
        assertTrue(DrowsinessRules.isLongDrive(now - DrowsinessRules.LONG_DRIVE_MS, now))
        assertFalse(DrowsinessRules.isLongDrive(now - DrowsinessRules.LONG_DRIVE_MS + 1, now))
        assertFalse(DrowsinessRules.isLongDrive(null, now))
    }

    // ── ⑥ 위험 시간대 ──
    @Test fun riskyHour() {
        assertTrue(DrowsinessRules.isRiskyHour(timeAt(3)))
        assertTrue(DrowsinessRules.isRiskyHour(timeAt(15)))
        assertFalse(DrowsinessRules.isRiskyHour(timeAt(10)))
        assertFalse(DrowsinessRules.isRiskyHour(timeAt(5)))
    }
}
