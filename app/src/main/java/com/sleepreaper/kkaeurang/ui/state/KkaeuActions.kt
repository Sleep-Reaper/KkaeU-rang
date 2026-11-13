package com.sleepreaper.kkaeurang.ui.state

import com.sleepreaper.kkaeurang.ui.model.SleepRange

/**
 * 화면에서 일어나는 사용자 동작. UI는 이 인터페이스로만 바깥에 알린다.
 * 지금은 FakeKkaeuStateSource가 로그 출력 + 상태 변경만 한다.
 */
interface KkaeuActions {
    /**
     * 화면의 "괜찮아요"·"터치로 응답하기" 버튼 (음성 응답의 백업 수단). 주의·경고·위험 어느 단계에서든
     * 운전자가 졸리지 않다고 알린 것으로 보고, 공급자는 경보 화면을 닫는다(alertDismissed) → 홈 계기판.
     * 진행 중인 질문이 있으면 "괜찮음"으로 대답하고, 경고음이 울리고 있으면 끈다. 점수·단계는 엔진이 정한다.
     */
    fun onAnswerOkay()

    /** TODO(integration): NaviHelper로 쉼터를 경유지에 추가 */
    fun onAddRestStopWaypoint()

    /** TODO(integration): 쉼터 안내 닫기를 판단 엔진/네비 쪽에 알림 (같은 쉼터를 바로 다시 띄우지 않도록) */
    fun onDismissRestStop()

    /** 위험 화면 "원래대로 되돌리기": 창문·찬바람을 원래대로 되돌리고 10분 동안 자동으로 다시 열지 않는다 */
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

    /** 출발 전 컨디션 질문: 화면 구간 버튼으로 대답 (마지막으로 몇 시간 잤는지) → 오늘의 시작 점수 */
    fun onStartCheckInAnswer(range: SleepRange)

    /** 출발 전 컨디션 질문 건너뛰기 → 100점에서 시작 */
    fun onStartCheckInSkip()
}
