package com.sleepreaper.kkaeurang.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sleepreaper.kkaeurang.R
import com.sleepreaper.kkaeurang.ui.model.DrowsinessUiState
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.ScoreChange
import com.sleepreaper.kkaeurang.ui.theme.KkaeuColors
import com.sleepreaper.kkaeurang.ui.theme.KkaeuType
import com.sleepreaper.kkaeurang.ui.theme.levelColor

private const val DIAL_HEIGHT_RATIO = 0.333f // 바깥 반지름 = 화면 높이의 1/3 (1210dp에서 약 400dp)
private const val RULE_WIDTH_RATIO = 1.67f    // 구분선 폭 = 바깥 반지름 × 1.67
private val EDGE = 40.dp
private val HEADER_HEIGHT = 80.dp
private val HEADER_GAP = 20.dp
private val BELOW_GAP = 36.dp                 // 호 끝에서 첫 구분선까지

/**
 * 메인 화면 가운데.
 * 위: 현재 단계 / 가운데: 계기판 / 아래: 마지막 점수 로그 / 맨 아래: 지금까지 쌓인 포인트
 *
 * 계기판 원의 중심을 이 영역의 정중앙에 두고, 단계는 그 위에, 로그·포인트는 호 아래에 붙인다.
 * 계기판을 길게 누르면 디버그 패널이 열린다 (onLongPress가 null이면 아무 일 없음).
 */
@Composable
fun CenterGauge(
    state: DrowsinessUiState,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cy = maxHeight / 2
        val ro = minOf(maxHeight * DIAL_HEIGHT_RATIO, maxWidth / 2 - EDGE, cy - EDGE - HEADER_HEIGHT - HEADER_GAP)

        StageHeader(
            level = state.level,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = cy - ro - HEADER_GAP - HEADER_HEIGHT)
                .height(HEADER_HEIGHT),
        )

        DrowsinessDial(
            score = state.score,
            level = state.level,
            modifier = Modifier
                .align(Alignment.Center)
                .size(ro * 2)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { currentOnLongPress?.invoke() })
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = cy + ro * 0.5f + BELOW_GAP)
                .width(ro * RULE_WIDTH_RATIO),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalDivider(thickness = 2.dp, color = KkaeuColors.Outline)
            ScoreLog(state.lastScoreChange, Modifier.padding(vertical = 22.dp))
            HorizontalDivider(thickness = 2.dp, color = KkaeuColors.Outline)
            TotalPointsLine(state.totalPoints, Modifier.padding(top = 20.dp))
        }
    }
}

/** "정상" (모양 아이콘 + 단계색 글자) */
@Composable
fun StageHeader(level: DrowsyLevel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        LevelIcon(level = level, modifier = Modifier.size(56.dp))
        Text(
            text = stringResource(level.labelRes()),
            style = KkaeuType.MainLevel,
            color = levelColor(level),
        )
    }
}

/** 마지막으로 점수가 바뀐 이유: "[핸들 고정 후 급조작 +25점]" */
@Composable
fun ScoreLog(change: ScoreChange?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.score_log_title),
            style = KkaeuType.MainLogTitle,
            color = KkaeuColors.TextSecondary,
        )
        Text(
            text = change?.let { stringResource(R.string.score_log_entry, it.reason, it.delta) }
                ?: stringResource(R.string.score_log_empty),
            style = KkaeuType.MainLog,
            color = if (change == null) KkaeuColors.TextSecondary else KkaeuColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "지금까지 쌓인 포인트 : 1,500P" */
@Composable
fun TotalPointsLine(totalPoints: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.main_points_label),
            style = KkaeuType.MainPointsLabel,
            color = KkaeuColors.TextSecondary,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = stringResource(R.string.main_points_value, totalPoints),
            style = KkaeuType.MainPointsValue,
            color = KkaeuColors.Points,
            modifier = Modifier.alignByBaseline(),
        )
    }
}
