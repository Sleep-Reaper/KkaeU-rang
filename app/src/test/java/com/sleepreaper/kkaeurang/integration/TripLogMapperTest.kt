package com.sleepreaper.kkaeurang.integration

import com.sleepreaper.kkaeurang.detector.LogEntry
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.TripLogKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripLogMapperTest {

    private fun map(msg: String) = TripLogMapper.fromEngine(LogEntry(1_000L, msg))

    @Test fun levelUp() {
        val e = map("단계 변경: 정상 → 주의 (점수 45)")
        assertEquals(TripLogKind.LEVEL_UP, e.kind)
        assertEquals(DrowsyLevel.CAUTION, e.level)
        assertEquals("안전 → 주의 단계 (안전 점수 55점)", e.message)
    }

    @Test fun levelDown() {
        val e = map("단계 변경: 경고 → 주의 (점수 67)")
        assertEquals(TripLogKind.LEVEL_DOWN, e.kind)
        assertEquals(DrowsyLevel.CAUTION, e.level)
    }

    @Test fun scoreEventBecomesSafetyDrop() {
        val e = map("핸들 고정 후 급조작 (+25점)")
        assertEquals(TripLogKind.EVENT, e.kind)
        assertEquals("핸들 고정 후 급조작", e.message)
        assertEquals(-25, e.safetyDelta)
    }

    @Test fun otherKinds() {
        assertEquals(TripLogKind.QUESTION, map("운전자에게 질문").kind)
        assertEquals(TripLogKind.ANSWER, map("운전자 응답: 괜찮음").kind)
        assertEquals(TripLogKind.ANSWER, map("무응답 → 경고 단계를 3분간 유지").kind)
        assertEquals(TripLogKind.ALARM, map("알람 시작").kind)
        assertEquals(TripLogKind.VEHICLE, map("창문 열기, 졸음쉼터 안내 요청").kind)
        assertEquals(TripLogKind.REST, map("정차(P단) → 경고 해제").kind)
        assertEquals(TripLogKind.CONDITION, map("출발 전 컨디션: 수면 4시간 미만, 기본 위험도 20점").kind)
        val other = map("뭔가 새로운 로그")
        assertEquals(TripLogKind.OTHER, other.kind)
        assertNull(other.safetyDelta)
    }
}
