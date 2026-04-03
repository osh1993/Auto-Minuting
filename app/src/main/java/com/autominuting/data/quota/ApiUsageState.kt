package com.autominuting.data.quota

/**
 * 대시보드 UI용 엔진별 API 사용량 집계 상태.
 */
data class ApiUsageState(
    val sttEngineStats: List<EngineCallStat>,
    val minutesEngineStats: List<EngineCallStat>
) {
    /** 전체 예상 비용 합계 (USD) */
    val totalEstimatedCostUsd: Double
        get() = (sttEngineStats + minutesEngineStats).sumOf { it.estimatedCostUsd }

    /** 사용 기록이 하나도 없는지 여부 */
    val isEmpty: Boolean
        get() = (sttEngineStats + minutesEngineStats).all { it.callCount == 0 }
}

/**
 * 단일 엔진의 호출 통계.
 * @param engineKey ApiUsageTracker.KEY_* 상수
 * @param displayName UI에 표시할 엔진 이름 (한국어 포함)
 * @param callCount 누적 호출 횟수
 * @param estimatedCostUsd 예상 비용 (callCount * costPerCall)
 */
data class EngineCallStat(
    val engineKey: String,
    val displayName: String,
    val callCount: Int,
    val estimatedCostUsd: Double
)
