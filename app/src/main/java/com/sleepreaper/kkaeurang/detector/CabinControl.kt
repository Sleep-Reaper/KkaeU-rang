package com.sleepreaper.kkaeurang.detector

/**
 * 졸음 대응용 실내 제어(운전석 창문 + 공조)의 약속.
 * 판단(몇 % 열지, 찬바람을 켤지)은 하지 않고, 시키는 대로 움직이고 차량 상태를 알려 주기만 한다.
 *
 * 구현체
 * - 실차: Pleos Vehicle SDK의 Window·HVAC·Wiper를 감싼 구현 (6단계에서 추가)
 * - 에뮬레이터·테스트: [com.sleepreaper.kkaeurang.integration.SimulatedCabinControl]
 *
 * 규칙
 * - 창문 열림 정도는 차종마다 다른 원시 값 대신 **0(완전히 닫힘) ~ 100(완전히 열림) %**로 주고받는다.
 * - 모든 호출과 콜백은 **메인 스레드**에서 일어난다. (차량 리스너가 다른 스레드여도 구현체가 메인으로 넘긴다)
 * - 결과 콜백(onResult)은 호출당 정확히 한 번 온다.
 */
interface CabinControl {

    /** 차량 연결·리스너 등록. 창문 범위 조회처럼 시간이 걸리는 준비도 여기서 시작한다. */
    fun start()

    /** 리스너 해제. 창문·공조를 원래대로 되돌리는 일은 하지 않는다(그건 판단하는 쪽 몫). */
    fun stop()

    /** 운전석 창문의 현재 열림 정도(%). 아직 모르거나 읽을 수 없으면 null */
    val windowOpenPercent: Int?

    /** 비가 오는지(와이퍼 작동 중). 알 수 없으면 false */
    val isRaining: Boolean

    /**
     * 운전석 창문 위치가 바뀔 때마다 불린다(%). **우리가 움직인 것과 운전자가 움직인 것 모두** 온다.
     * 둘을 구분하는 건 판단하는 쪽 몫이다.
     */
    var onWindowChanged: ((percent: Int) -> Unit)?

    /** 운전석 창문을 [percent]%로 움직인다(0~100 밖이면 잘라서 쓴다). 실패하면 false */
    fun setWindowOpenPercent(percent: Int, onResult: (success: Boolean) -> Unit)

    /**
     * 찬바람(에어컨 + 외기 유입 + 얼굴 방향 + 센 풍량)을 켜거나 끈다. 실패하면 false
     * - 켤 때: 켜기 직전 공조 상태를 구현체가 기억해 둔다.
     * - 끌 때: 기억해 둔 상태로 되돌린다. (켠 적이 없으면 아무것도 하지 않고 성공으로 본다)
     */
    fun setCoolAir(on: Boolean, onResult: (success: Boolean) -> Unit)
}
