package com.sleepreaper.kkaeurang.integration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 포그라운드 서비스([DrowsinessService])가 만든 [KkaeuLiveSource]를 화면이 가져다 쓰는 보관소.
 *
 * - 서비스가 시작되면 [publish], 종료되면 [clear]를 부른다. (그 외에는 건드리지 않는다)
 * - 화면은 [source]를 구독한다. 값이 null이면 서비스가 아직 안 떴거나 종료된 상태이다.
 * - 화면(Activity)이 닫히거나 회전돼도 서비스가 살아 있는 한 같은 source가 유지되어
 *   점수·로그·포인트가 초기화되지 않는다.
 *
 * 모든 접근은 메인 스레드에서 한다.
 */
object KkaeuRuntime {
    private val _source = MutableStateFlow<KkaeuLiveSource?>(null)
    val source: StateFlow<KkaeuLiveSource?> = _source.asStateFlow()

    internal fun publish(source: KkaeuLiveSource) {
        _source.value = source
    }

    internal fun clear() {
        _source.value = null
    }
}
