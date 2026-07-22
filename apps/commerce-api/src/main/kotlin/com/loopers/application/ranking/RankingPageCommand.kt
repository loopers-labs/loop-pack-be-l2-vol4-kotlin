package com.loopers.application.ranking

import java.time.LocalDate

data class RankingPageCommand(
    /** null이면 오늘(Asia/Seoul) 기준으로 조회한다. */
    val date: LocalDate?,
    val page: Int,
    val size: Int,
    val period: PeriodType = PeriodType.DAILY,
) {
    /** 유스케이스 입력 표현. 도메인의 RankingPeriod(주간/월간 전용)와는 어댑터에서 명시적으로 변환한다. */
    enum class PeriodType { DAILY, WEEKLY, MONTHLY }
}
