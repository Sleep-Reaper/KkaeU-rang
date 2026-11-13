package com.sleepreaper.kkaeurang.ui.fake

import com.sleepreaper.kkaeurang.ui.model.AlertCounts
import com.sleepreaper.kkaeurang.ui.model.BenefitKind
import com.sleepreaper.kkaeurang.ui.model.BenefitUse
import com.sleepreaper.kkaeurang.ui.model.BenefitsSummary
import com.sleepreaper.kkaeurang.ui.model.DailyScore
import com.sleepreaper.kkaeurang.ui.model.DriveLogSummary
import com.sleepreaper.kkaeurang.ui.model.DrowsyLevel
import com.sleepreaper.kkaeurang.ui.model.FuelBenefit
import com.sleepreaper.kkaeurang.ui.model.FuelTier
import com.sleepreaper.kkaeurang.ui.model.InsuranceBenefit
import com.sleepreaper.kkaeurang.ui.model.InsuranceTier
import com.sleepreaper.kkaeurang.ui.model.TripSummary
import com.sleepreaper.kkaeurang.ui.state.RecordsRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * "주행 로그"·"혜택" 탭 임시 값. 화면 모양을 보기 위한 값일 뿐 실제 기록이 아니다.
 * 날짜는 오늘을 기준으로 만든다 (언제 열어도 "최근 14일"이 자연스럽게 보이도록).
 *
 * 혜택 조건은 정해지지 않아 커넥티드카 안전운전 보험 특약(안전운전 점수 70점 이상이면 보험료 할인)
 * 같은 방식을 참고해 지어낸 예시다. 화면에도 "예시"라고 적는다.
 * TODO(integration): 실제 저장소·제휴 API를 구현한 RecordsRepository로 바꾼다.
 */
object SampleRecords : RecordsRepository {

    override fun driveLog(): DriveLogSummary {
        val today = LocalDate.now()
        // 최근 14일 (null = 그날 주행 없음). 마지막이 오늘
        val scores = listOf(91, 94, null, 88, 92, 95, 97, 90, null, 86, 93, 96, 94, 98)
        val minutes = listOf(52, 85, 0, 130, 38, 64, 47, 92, 0, 141, 58, 44, 73, 36)
        return DriveLogSummary(
            since = today.minusDays(256),
            totalDriveMinutes = 278 * 60 + 30,
            totalDistanceKm = 12_430f,
            tripCount = 142,
            averageScore = 93,
            averageScoreChange = 2,
            safeStreakDays = 9,
            bestStreakDays = 21,
            dailyScores = scores.indices.map { i ->
                DailyScore(today.minusDays((scores.size - 1 - i).toLong()), scores[i], minutes[i])
            },
            alertCounts = AlertCounts(caution = 12, warning = 4, critical = 1),
            avgResponseMs = 1_800,
            restCount = 23,
            totalEarnedPoints = 16_120,
            hourlyAlerts = listOf(3, 4, 1, 0, 2, 5, 1, 1),
            recentTrips = listOf(
                trip(today.minusDays(1), 18, 40, 52, 41.2f, 96, DrowsyLevel.NORMAL, 52),
                trip(today.minusDays(2), 8, 10, 85, 98.7f, 88, DrowsyLevel.CAUTION, 71),
                trip(today.minusDays(4), 14, 20, 130, 186.3f, 82, DrowsyLevel.WARNING, 98),
                trip(today.minusDays(5), 7, 55, 38, 27.9f, 97, DrowsyLevel.NORMAL, 38),
            ),
        )
    }

    override fun benefits(): BenefitsSummary {
        val today = LocalDate.now()
        val history = listOf(
            BenefitUse(today.minusDays(3), BenefitKind.FUEL, "주유 포인트 결제", 3_000),
            BenefitUse(today.minusDays(11), BenefitKind.FUEL, "주유 리터당 할인 (42L)", 4_200),
            BenefitUse(today.minusDays(16), BenefitKind.CAFE, "휴게소 커피 교환권", 4_500),
            BenefitUse(today.minusDays(29), BenefitKind.FUEL, "주유 포인트 결제", 5_000),
            BenefitUse(today.minusDays(41), BenefitKind.REST_STOP, "휴게소 식사 할인", 2_000),
        )
        return BenefitsSummary(
            receivedTotalWon = history.sumOf { it.amountWon },
            monthEarnedPoints = 1_240,
            fuel = FuelBenefit(
                wonPerPoint = 1,
                partnerText = "전국 제휴 주유소 2,400곳",
                tiers = listOf(FuelTier(90, 100), FuelTier(80, 70), FuelTier(70, 40)),
                savedWon = history.filter { it.kind == BenefitKind.FUEL }.sumOf { it.amountWon },
            ),
            insurance = InsuranceBenefit(
                programName = "안전운전 할인 특약",
                averageScore = 93,
                tiers = listOf(InsuranceTier(70, 5), InsuranceTier(80, 8), InsuranceTier(90, 10)),
                annualPremiumWon = 800_000,
                minDistanceKm = 500,
            ),
            history = history,
        )
    }

    private fun trip(
        date: LocalDate,
        hour: Int,
        minute: Int,
        driveMinutes: Int,
        km: Float,
        score: Int,
        maxLevel: DrowsyLevel,
        points: Int,
    ) = TripSummary(
        startedAtMs = date.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        driveMinutes = driveMinutes,
        distanceKm = km,
        averageScore = score,
        maxLevel = maxLevel,
        earnedPoints = points,
    )
}
