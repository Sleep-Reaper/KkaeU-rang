package com.sleepreaper.kkaeurang.integration

import com.sleepreaper.kkaeurang.ui.model.SleepRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepAnswerParserTest {

    private fun parse(t: String?) = SleepAnswerParser.parse(t)

    @Test fun digits() {
        assertEquals(SleepRange.UNDER_4H, parse("3시간"))
        assertEquals(SleepRange.H4_TO_6, parse("5시간 잤어요"))
        assertEquals(SleepRange.H6_TO_8, parse("7 시간 정도 잤어"))
        assertEquals(SleepRange.OVER_8H, parse("8시간"))
        assertEquals(SleepRange.OVER_8H, parse("10시간 넘게 잤어"))
    }

    @Test fun koreanNumbers() {
        assertEquals(SleepRange.UNDER_4H, parse("세 시간이요"))
        assertEquals(SleepRange.H4_TO_6, parse("다섯시간"))
        assertEquals(SleepRange.H6_TO_8, parse("여섯 시간 잤어요"))
        assertEquals(SleepRange.OVER_8H, parse("여덟 시간"))
        assertEquals(SleepRange.OVER_8H, parse("열 시간 정도"))
    }

    @Test fun halfHour() {
        assertEquals(SleepRange.UNDER_4H, parse("3시간 반"))   // 3.5 → 4시간 미만
        assertEquals(SleepRange.H6_TO_8, parse("7시간반"))
    }

    @Test fun ranges() {
        assertEquals(SleepRange.H4_TO_6, parse("4에서 6시간"))
        assertEquals(SleepRange.H6_TO_8, parse("6~8시간"))
        assertEquals(SleepRange.UNDER_4H, parse("4시간 미만"))
        assertEquals(SleepRange.OVER_8H, parse("8시간 이상"))
    }

    @Test fun onlyVersusLessThan() {
        assertEquals(SleepRange.H4_TO_6, parse("4시간밖에 못 잤어"))   // 딱 4시간
        assertEquals(SleepRange.UNDER_4H, parse("4시간도 못 잤어"))    // 4시간이 안 됨
        assertEquals(SleepRange.H4_TO_6, parse("6시간 안 됐어"))
    }

    @Test fun barelySlept() {
        assertEquals(SleepRange.UNDER_4H, parse("한숨도 못 잤어"))
        assertEquals(SleepRange.UNDER_4H, parse("거의 못 잤어요"))
        assertEquals(SleepRange.UNDER_4H, parse("얼마 안 잤어"))
    }

    @Test fun notUnderstood() {
        assertNull(parse(null))
        assertNull(parse(""))
        assertNull(parse("모르겠어"))
        assertNull(parse("푹 잤어"))
        assertNull(parse("새벽 2시에 잤어"))   // 잠든 시각만으로는 몇 시간인지 알 수 없어 버튼으로
    }
}
