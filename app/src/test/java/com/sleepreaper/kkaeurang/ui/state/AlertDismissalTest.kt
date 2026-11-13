package com.sleepreaper.kkaeurang.ui.state

import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDismissalTest {

    @Test fun notDismissedByDefault() {
        val a = AlertDismissal()
        assertFalse(a.update(DrowsyLevel.CAUTION, 45f))
    }

    @Test fun dismissKeepsHomeAtSameLevel() {
        val a = AlertDismissal()
        a.dismiss(DrowsyLevel.CAUTION, 45f)
        assertTrue(a.update(DrowsyLevel.CAUTION, 44f))
        assertTrue(a.update(DrowsyLevel.CAUTION, 43f))
    }

    @Test fun dismissIgnoredWhenNormal() {
        val a = AlertDismissal()
        a.dismiss(DrowsyLevel.NORMAL, 10f)
        assertFalse(a.isDismissed)
    }

    @Test fun higherLevelShowsAlertAgain() {
        val a = AlertDismissal()
        a.dismiss(DrowsyLevel.CAUTION, 45f)
        assertFalse(a.update(DrowsyLevel.WARNING, 52f))
        // 한 번 풀리면 다시 닫을 때까지 경보 화면
        assertFalse(a.update(DrowsyLevel.CAUTION, 45f))
    }

    @Test fun lowerLevelStaysDismissed() {
        val a = AlertDismissal()
        a.dismiss(DrowsyLevel.WARNING, 72f)
        assertTrue(a.update(DrowsyLevel.CAUTION, 66f))
    }

    @Test fun backToNormalClears() {
        val a = AlertDismissal()
        a.dismiss(DrowsyLevel.CAUTION, 45f)
        assertFalse(a.update(DrowsyLevel.NORMAL, 37f))
        assertFalse(a.update(DrowsyLevel.CAUTION, 41f))
    }

    @Test fun newDrowsySignalAtSameLevelShowsAlertAgain() {
        val a = AlertDismissal()
        a.dismiss(DrowsyLevel.CAUTION, 50f)
        assertTrue(a.update(DrowsyLevel.CAUTION, 44f))   // 점수가 내려가면 기준도 내려간다
        assertTrue(a.update(DrowsyLevel.CAUTION, 53f))   // 44 + 9
        assertFalse(a.update(DrowsyLevel.CAUTION, 54f))  // 44 + 10 → 새 졸음 신호
    }

    @Test fun criticalDismissStillReAlertsOnNewSignal() {
        val a = AlertDismissal()
        a.dismiss(DrowsyLevel.CRITICAL, 82f)
        assertTrue(a.update(DrowsyLevel.CRITICAL, 81f))
        assertFalse(a.update(DrowsyLevel.CRITICAL, 96f))
    }
}
