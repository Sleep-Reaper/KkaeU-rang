package com.sleepreaper.kkaeurang.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.components.CardChip
import com.sleepreaper.kkaeurang.ui.components.CardIconBrush
import com.sleepreaper.kkaeurang.ui.components.CardRule
import com.sleepreaper.kkaeurang.ui.components.ChevronCircle
import com.sleepreaper.kkaeurang.ui.components.KGradientIcon
import com.sleepreaper.kkaeurang.ui.components.KpiCard
import com.sleepreaper.kkaeurang.ui.components.LOG_GAP
import com.sleepreaper.kkaeurang.ui.components.LOG_KPI_H
import com.sleepreaper.kkaeurang.ui.components.LogPageTitle
import com.sleepreaper.kkaeurang.ui.components.lavenderCard
import com.sleepreaper.kkaeurang.ui.components.valueWithUnit
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.LocalDesign
import com.sleepreaper.kkaeurang.ui.theme.W

/**
 * 마이 탭: 내 정보와 운전 요약.
 *
 * [프로필 카드] 마스코트 · 닉네임 · 등급 · 차량 · 함께한 날
 * [숫자 4장] 총 주행 시간 · 총 주행 거리 · 평균 점수 · 연속 안전 주행
 * [메뉴] 내 차량 정보 · 수면 기록 연동 · 비상 연락처 · 공지사항
 *
 * TODO(data): 모두 임시값. 실제 사용자·주행 기록이 생기면 state에서 받아 오도록 바꾼다.
 * TODO(action): 메뉴 항목은 아직 눌러도 아무 동작 없음.
 */
@Composable
fun MyTab(modifier: Modifier = Modifier) {
    val d = LocalDesign.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = d.dp(EDGE_LEFT_PX), end = d.dp(EDGE_RIGHT_PX), top = d.dp(4), bottom = d.dp(36)),
        verticalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP)),
    ) {
        LogPageTitle(
            icon = R.drawable.ic_k_person,
            title = "마이",
            subtitle = "내 정보와 운전 요약",
        )

        ProfileCard()

        // 숫자 4장 (홈 시안의 "나의 주행 로그"와 같은 임시값)
        Row(horizontalArrangement = Arrangement.spacedBy(d.dp(LOG_GAP))) {
            val kpi = Modifier.weight(1f).height(d.dp(LOG_KPI_H))
            KpiCard(R.drawable.ic_k_car, "총 주행 시간", valueWithUnit("278", "시간"), "깨우랑과 함께", kpi)
            KpiCard(R.drawable.ic_k_road, "총 주행 거리", valueWithUnit("12,430", "km"), "누적", kpi)
            KpiCard(R.drawable.ic_k_star, "평균 점수", valueWithUnit("93", "점"), "전체 주행 평균", kpi)
            KpiCard(R.drawable.ic_k_shield, "연속 안전 주행", valueWithUnit("12", "일"), "위험 단계 없이", kpi)
        }

        MenuCard(
            items = listOf(
                MyMenu(R.drawable.ic_k_car, "내 차량 정보", "아이오닉 5 · 12가 3456"),
                MyMenu(R.drawable.ic_ms_bedtime, "수면 기록 연동", "연결 안 됨"),
                MyMenu(R.drawable.ic_ms_notifications_active, "비상 연락처", "1명 등록"),
                MyMenu(R.drawable.ic_k_list, "공지사항", null),
            ),
        )
    }
}

/** 프로필: 마스코트 + 닉네임·등급 + 차량 + 함께한 날 */
@Composable
private fun ProfileCard() {
    val d = LocalDesign.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lavenderCard(d.dp(26))
            .padding(horizontal = d.dp(36), vertical = d.dp(28)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.img_mascot),
            contentDescription = null,
            modifier = Modifier.size(d.dp(120)),
        )
        Spacer(Modifier.width(d.dp(28)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(d.dp(6))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("깨우랑 드라이버", style = d.text(36, W.ExtraBold, KkaeuColors.CardText), maxLines = 1)
                Spacer(Modifier.width(d.dp(16)))
                CardChip("안전 Lv.4", strong = true)
            }
            Text("아이오닉 5 · 12가 3456", style = d.text(24, W.SemiBold, KkaeuColors.CardTextSub), maxLines = 1)
            Text("깨우랑과 함께한 지 32일", style = d.text(21, W.Medium, KkaeuColors.CardTextSub), maxLines = 1)
        }
    }
}

private data class MyMenu(@param:DrawableRes val icon: Int, val title: String, val sub: String?)

/** 메뉴 목록: 한 카드 안에 줄 + 구분선 */
@Composable
private fun MenuCard(items: List<MyMenu>) {
    val d = LocalDesign.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lavenderCard(d.dp(26))
            .padding(horizontal = d.dp(36), vertical = d.dp(10)),
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) CardRule()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* TODO(action): 메뉴 화면 */ }
                    .padding(vertical = d.dp(22)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KGradientIcon(item.icon, d.dp(40), CardIconBrush)
                Spacer(Modifier.width(d.dp(22)))
                Text(item.title, style = d.text(28, W.ExtraBold, KkaeuColors.CardText), modifier = Modifier.weight(1f), maxLines = 1)
                if (item.sub != null) {
                    Text(item.sub, style = d.text(21, W.Medium, KkaeuColors.CardTextSub), maxLines = 1)
                    Spacer(Modifier.width(d.dp(18)))
                }
                ChevronCircle(d.dp(40))
            }
        }
    }
}