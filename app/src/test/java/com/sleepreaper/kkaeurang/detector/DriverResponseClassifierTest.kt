package com.sleepreaper.kkaeurang.detector

import org.junit.Assert.assertEquals
import org.junit.Test

class DriverResponseClassifierTest {

    private fun said(text: String) = DriverResponseClassifier.classify(VoiceAnswer(text, false, 1500L))

    @Test fun ok_whenSaysFine() {
        assertEquals(DriverResponse.OK, said("괜찮아"))
        assertEquals(DriverResponse.OK, said("응 괜찮아"))
        assertEquals(DriverResponse.OK, said("그 나는 괜찮아?"))     // 실제 오인식 예시
        assertEquals(DriverResponse.OK, said("아니 괜찮아"))
    }

    @Test fun admitted_whenSaysSleepyOrTired() {
        assertEquals(DriverResponse.ADMITTED, said("졸려"))
        assertEquals(DriverResponse.ADMITTED, said("조금 피곤해요"))
        assertEquals(DriverResponse.ADMITTED, said("힘들어"))
    }

    @Test fun admitted_whenSaysNotFine() {
        // "괜찮"이 들어 있어도 부정 표현이면 졸림 인정으로 본다
        assertEquals(DriverResponse.ADMITTED, said("안 괜찮아"))
        assertEquals(DriverResponse.ADMITTED, said("괜찮지 않아"))
        assertEquals(DriverResponse.ADMITTED, said("안 좋아"))
    }

    @Test fun ok_whenDeniesSleepiness() {
        // "졸"이 들어 있어도 졸림을 부정하면 괜찮은 것
        assertEquals(DriverResponse.OK, said("안 졸려"))
        assertEquals(DriverResponse.OK, said("졸리지 않아"))
        assertEquals(DriverResponse.OK, said("하나도 안 피곤해"))
        assertEquals(DriverResponse.OK, said("괜찮아 안 졸려"))
    }

    @Test fun unclear_whenNoKeyword() {
        assertEquals(DriverResponse.UNCLEAR, said("음 글쎄요"))
    }

    @Test fun timeoutVariants() {
        assertEquals(DriverResponse.SLOW,
            DriverResponseClassifier.classify(VoiceAnswer(null, true, 2000L)))
        assertEquals(DriverResponse.NO_ANSWER,
            DriverResponseClassifier.classify(VoiceAnswer(null, true, null)))
    }

    @Test fun error_whenRecognitionFailed() {
        assertEquals(DriverResponse.ERROR,
            DriverResponseClassifier.classify(VoiceAnswer(null, false, null)))
    }
}
