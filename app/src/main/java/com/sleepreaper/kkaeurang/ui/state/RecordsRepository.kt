package com.sleepreaper.kkaeurang.ui.state

import com.sleepreaper.kkaeurang.ui.model.BenefitsSummary
import com.sleepreaper.kkaeurang.ui.model.DriveLogSummary

/**
 * "주행 로그"·"혜택" 탭 데이터를 주는 곳. 상태 공급자(KkaeuLiveSource·FakeKkaeuStateSource)가 읽어
 * DrowsinessUiState.driveLog / benefits로 내려 준다.
 *
 * 지금은 [com.sleepreaper.kkaeurang.ui.fake.SampleRecords](임시 값)만 있다.
 * TODO(integration): 주행 기록 저장(Room 등)과 제휴 혜택 API가 생기면 이 인터페이스를 구현해 바꿔 끼운다.
 *  주행이 끝날 때마다 다시 읽도록 공급자에서 [driveLog]를 다시 부르면 된다.
 */
interface RecordsRepository {
    fun driveLog(): DriveLogSummary
    fun benefits(): BenefitsSummary
}
