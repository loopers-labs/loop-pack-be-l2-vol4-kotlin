package com.loopers.batch.job.productrank

import org.springframework.batch.core.JobParametersInvalidException
import org.springframework.batch.core.JobParametersValidator
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** productRank* Job들의 공통 JobParameter 해석. period는 필수, targetDate는 생략 시 오늘(KST). */
object ProductRankJobParams {
    private val ZONE = ZoneId.of("Asia/Seoul")

    fun resolvePeriod(period: String?): RankPeriod {
        requireNotNull(period) { "period 파라미터는 필수다 (WEEKLY | MONTHLY)." }
        return RankPeriod.valueOf(period)
    }

    fun resolveWindow(period: RankPeriod, targetDate: String?): AggregationWindow =
        period.windowFor(targetDate?.let(LocalDate::parse) ?: LocalDate.now(ZONE))

    /** 첫 Step(staging TRUNCATE 등)이 실행되기 전에 파라미터 오류를 걸러내는 Job 레벨 검증. */
    fun validator(): JobParametersValidator = JobParametersValidator { parameters ->
        try {
            val period = resolvePeriod(parameters?.getString("period"))
            resolveWindow(period, parameters?.getString("targetDate"))
        } catch (e: IllegalArgumentException) {
            throw JobParametersInvalidException(e.message ?: "잘못된 Job 파라미터")
        } catch (e: DateTimeParseException) {
            throw JobParametersInvalidException("targetDate는 yyyy-MM-dd 형식이어야 한다. 입력=${e.parsedString}")
        }
    }
}
