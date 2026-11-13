package com.sleepreaper.kkaeurang.ui.model

import java.time.LocalDate

// "주행 로그"·"혜택" 탭이 보는 값.
// 지금은 SampleRecords(임시 값)가 채우고, 주행 기록 저장소·제휴 혜택 API가 생기면
// RecordsRepository 구현체만 바꿔 끼우면 화면은 그대로 쓸 수 있다.

// ───────────────────────── 주행 로그 ─────────────────────────

/** 지금까지의 모든 주행을 모은 기록 (주행 로그 탭) */
data class DriveLogSummary(
    val since: LocalDate?,              // 깨우랑과 처음 달린 날
    val totalDriveMinutes: Int,         // 깨우랑과 함께한 시간
    val totalDistanceKm: Float,
    val tripCount: Int,
    val averageScore: Int?,             // 운전(안전) 점수 평균
    val averageScoreChange: Int?,       // 지난달 대비 평균 변화 (+2 등)
    val safeStreakDays: Int,            // 경고 없이 달린 연속 일수
    val bestStreakDays: Int,
    val dailyScores: List<DailyScore>,  // 날짜별 운전 점수 (오래된 날부터)
    val alertCounts: AlertCounts,       // 졸음 경보 횟수
    val avgResponseMs: Long?,           // 질문에 대답하기까지 평균 시간
    val restCount: Int,                 // 쉼터에서 쉰 횟수
    val totalEarnedPoints: Int,         // 지금까지 모은 포인트
    val hourlyAlerts: List<Int>,        // 3시간 단위 8칸 (0~3시, 3~6시 …) 졸음 경보 횟수
    val recentTrips: List<TripSummary>, // 최근 주행 (최신부터)
) {
    companion object {
        val Empty = DriveLogSummary(
            since = null,
            totalDriveMinutes = 0,
            totalDistanceKm = 0f,
            tripCount = 0,
            averageScore = null,
            averageScoreChange = null,
            safeStreakDays = 0,
            bestStreakDays = 0,
            dailyScores = emptyList(),
            alertCounts = AlertCounts(),
            avgResponseMs = null,
            restCount = 0,
            totalEarnedPoints = 0,
            hourlyAlerts = List(8) { 0 },
            recentTrips = emptyList(),
        )
    }
}

/** 하루 운전 점수. 그날 주행이 없으면 [score]가 null */
data class DailyScore(val date: LocalDate, val score: Int?, val driveMinutes: Int = 0)

/** 지난 주행 한 건 요약 (주행 로그 탭 "최근 주행") */
data class TripSummary(
    val startedAtMs: Long,
    val driveMinutes: Int,
    val distanceKm: Float,
    val averageScore: Int,
    val maxLevel: DrowsyLevel,
    val earnedPoints: Int,
)

// ───────────────────────── 혜택 ─────────────────────────

/** 포인트·제휴 혜택 (혜택 탭). 보유 포인트는 DrowsinessUiState.totalPoints를 쓴다 */
data class BenefitsSummary(
    val receivedTotalWon: Int,          // 지금까지 포인트·할인으로 받은 혜택 (원)
    val monthEarnedPoints: Int,         // 이번 달 적립 포인트
    val fuel: FuelBenefit,
    val insurance: InsuranceBenefit,
    val history: List<BenefitUse>,      // 혜택 받은 내역 (최신부터)
) {
    companion object {
        val Empty = BenefitsSummary(
            receivedTotalWon = 0,
            monthEarnedPoints = 0,
            fuel = FuelBenefit(wonPerPoint = 1, partnerText = "", tiers = emptyList(), savedWon = 0),
            insurance = InsuranceBenefit(programName = "", averageScore = null, tiers = emptyList(), annualPremiumWon = 0, minDistanceKm = 0),
            history = emptyList(),
        )
    }
}

/** 주유 할인: 포인트 결제(1P = [wonPerPoint]원) + 안전 점수 구간별 리터당 할인 */
data class FuelBenefit(
    val wonPerPoint: Int,
    val partnerText: String,            // 제휴 주유소 안내 ("전국 제휴 주유소 2,400곳" 등)
    val tiers: List<FuelTier>,          // 점수가 높은 구간부터
    val savedWon: Int,                  // 지금까지 주유 할인 받은 금액
)

data class FuelTier(val minScore: Int, val wonPerLiter: Int)

/** 보험료 할인: 안전운전 점수 연동 특약 (커넥티드카 안전운전 할인 같은 방식) */
data class InsuranceBenefit(
    val programName: String,
    val averageScore: Int?,             // 보험 할인 판정에 쓰는 평균 안전 점수
    val tiers: List<InsuranceTier>,     // 점수가 낮은 구간부터
    val annualPremiumWon: Int,          // 예상 할인액 계산 기준 연 보험료
    val minDistanceKm: Int,             // 할인 조건: 누적 주행 거리
)

data class InsuranceTier(val minScore: Int, val discountPercent: Int)

enum class BenefitKind { FUEL, INSURANCE, REST_STOP, CAFE }

data class BenefitUse(
    val date: LocalDate,
    val kind: BenefitKind,
    val title: String,
    val amountWon: Int,
)
