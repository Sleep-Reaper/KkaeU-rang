package com.sleepreaper.kkaeurang.ui.state

import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * UI에 상태를 공급하는 쪽. UI는 [state] 하나만 보고 그린다.
 *
 * 지금은 FakeKkaeuStateSource가 구현한다.
 * TODO(integration): 판단 엔진(점수·단계·근거), 음성(VoiceModule 질문/응답/알람), 차량 제어(창문 등),
 *  네비(쉼터 정보)를 모아 DrowsinessUiState로 합쳐 내보내는 실제 구현체로 교체.
 *  - RESULT 상태는 2초 뒤 IDLE로, RESUMED는 2~3초 뒤 ACCRUING으로 되돌리는 것도 공급자 책임이다.
 *  - vehicleEvent는 화면에서 5초만 보여 주므로, 다음 이벤트를 다시 띄우려면 그 사이에 null로 비워 줘야 한다.
 */
interface KkaeuStateSource {
    val state: StateFlow<DrowsinessUiState>
}
