package com.sleepreaper.kkaeurang.detector

import org.junit.Assert.assertEquals
import org.junit.Test

/** 안전 점수(100에서 감점)를 단계로 바꾸는 기준과, 좋아질 때의 여유(2점) 확인 */
class ScoreThresholdsTest {

    @Test
    fun `점수만으로 정한 단계 경계`() {
        assertEquals(Level.NORMAL, ScoreThresholds.levelOf(100))
        assertEquals(Level.NORMAL, ScoreThresholds.levelOf(61))
        assertEquals(Level.CAUTION, ScoreThresholds.levelOf(60))
        assertEquals(Level.CAUTION, ScoreThresholds.levelOf(31))
        assertEquals(Level.WARNING, ScoreThresholds.levelOf(30))
        assertEquals(Level.WARNING, ScoreThresholds.levelOf(21))
        assertEquals(Level.DANGER, ScoreThresholds.levelOf(20))
        assertEquals(Level.DANGER, ScoreThresholds.levelOf(0))
    }

    @Test
    fun `나빠질 때는 기준에서 바로 단계가 오른다`() {
        assertEquals(Level.CAUTION, ScoreThresholds.levelFor(60, Level.NORMAL))
        assertEquals(Level.WARNING, ScoreThresholds.levelFor(30, Level.CAUTION))
        assertEquals(Level.DANGER, ScoreThresholds.levelFor(20, Level.WARNING))
        assertEquals(Level.DANGER, ScoreThresholds.levelFor(20, Level.NORMAL))
    }

    @Test
    fun `좋아질 때는 기준보다 2점 더 올라야 단계가 내려간다`() {
        // 위험(20 이하): 22까지는 유지, 23부터 경고
        assertEquals(Level.DANGER, ScoreThresholds.levelFor(22, Level.DANGER))
        assertEquals(Level.WARNING, ScoreThresholds.levelFor(23, Level.DANGER))
        // 경고(30 이하): 32까지는 유지, 33부터 주의
        assertEquals(Level.WARNING, ScoreThresholds.levelFor(32, Level.WARNING))
        assertEquals(Level.CAUTION, ScoreThresholds.levelFor(33, Level.WARNING))
        // 주의(60 이하): 62까지는 유지, 63부터 정상
        assertEquals(Level.CAUTION, ScoreThresholds.levelFor(62, Level.CAUTION))
        assertEquals(Level.NORMAL, ScoreThresholds.levelFor(63, Level.CAUTION))
    }

    @Test
    fun `한 번에 크게 회복하면 여유와 상관없이 해당 단계로 내려간다`() {
        assertEquals(Level.NORMAL, ScoreThresholds.levelFor(100, Level.DANGER))
        assertEquals(Level.CAUTION, ScoreThresholds.levelFor(50, Level.DANGER))
    }

    @Test
    fun `정상에서는 여유가 필요 없다`() {
        assertEquals(Level.NORMAL, ScoreThresholds.levelFor(100, Level.NORMAL))
        assertEquals(Level.NORMAL, ScoreThresholds.levelFor(61, Level.NORMAL))
    }
}
