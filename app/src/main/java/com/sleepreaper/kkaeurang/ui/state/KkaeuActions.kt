package com.sleepreaper.kkaeurang.ui.state

/**
 * 화면에서 일어나는 사용자 동작. UI는 이 인터페이스로만 바깥에 알린다.
 * 지금은 FakeKkaeuStateSource가 로그 출력 + 상태 변경만 한다.
 */
interface KkaeuActions {
    /**
     * 화면의 "괜찮아요"·"터치로 응답하기" 버튼 (음성 응답의 백업 수단).
     * TODO(integration): 판단 엔진에 "운전자 응답"으로 전달 (점수 감소·적립 재개 판단은 엔진이),
     *  진행 중인 VoiceModule 질문/알람 정리는 MainActivity가 가진 VoiceModule로.
     */
    fun onAnswerOkay()

    /** TODO(integration): NaviHelper로 쉼터를 경유지에 추가 */
    fun onAddRestStopWaypoint()

    /** TODO(integration): 쉼터 안내 닫기를 판단 엔진/네비 쪽에 알림 (같은 쉼터를 바로 다시 띄우지 않도록) */
    fun onDismissRestStop()

    /** TODO(integration): 차량 제어 모듈에 직전 제어(창문 열기·찬바람 등) 되돌리기 요청 */
    fun onCancelVehicleControl()

    /** TODO(integration): 주행 기록 저장 후 리포트 상태 정리 */
    fun onCloseTripReport()

    /** 헤더 오른쪽 "경고음" 스위치 (VoiceModule 알람·신호음 음소거) */
    fun onToggleWarningSound()

    /**
     * 경고 단계 "음성으로 답하기". 경고음을 멈추고 운전자에게 한 번 물어 대답을 듣는다.
     * 괜찮다고 하면 경고음을 끈 채로 두고, 아니면(무응답·졸림) 경고음을 다시 울린다.
     */
    fun onVoiceAnswer()

    /** 홈 "근처 쉼터 찾기" 카드. 내비에 가까운 쉼터 안내를 요청한다 */
    fun onFindRestStop()
}
