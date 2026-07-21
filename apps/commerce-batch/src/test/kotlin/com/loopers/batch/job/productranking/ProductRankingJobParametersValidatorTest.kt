package com.loopers.batch.job.productranking

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.JobParametersInvalidException
import java.time.LocalDate

class ProductRankingJobParametersValidatorTest {
    private val weeklyValidator = ProductRankingJobParametersValidator(
        period = ProductRankingPeriod.WEEKLY,
        periodPolicy = ProductRankingPeriodPolicy(),
    )
    private val monthlyValidator = ProductRankingJobParametersValidator(
        period = ProductRankingPeriod.MONTHLY,
        periodPolicy = ProductRankingPeriodPolicy(),
    )

    @DisplayName("baseDate Job parameter가 없으면 실패한다")
    @Test
    fun failsWhenBaseDateIsMissing() {
        val jobParameters = JobParametersBuilder().toJobParameters()

        assertThatThrownBy { weeklyValidator.validate(jobParameters) }
            .isInstanceOf(JobParametersInvalidException::class.java)
            .hasMessageContaining("baseDate")
    }

    @DisplayName("baseDate Job parameter 형식이 yyyy-MM-dd가 아니면 실패한다")
    @Test
    fun failsWhenBaseDateFormatIsInvalid() {
        val jobParameters = JobParametersBuilder()
            .addString("baseDate", "20260803")
            .toJobParameters()

        assertThatThrownBy { weeklyValidator.validate(jobParameters) }
            .isInstanceOf(JobParametersInvalidException::class.java)
            .hasMessageContaining("yyyy-MM-dd")
    }

    @DisplayName("Weekly baseDate가 월요일이면 통과한다")
    @Test
    fun passesWhenWeeklyBaseDateIsMonday() {
        val jobParameters = JobParametersBuilder()
            .addLocalDate("baseDate", LocalDate.of(2026, 8, 3))
            .toJobParameters()

        assertThatCode { weeklyValidator.validate(jobParameters) }
            .doesNotThrowAnyException()
    }

    @DisplayName("Monthly baseDate가 1일이면 통과한다")
    @Test
    fun passesWhenMonthlyBaseDateIsFirstDay() {
        val jobParameters = JobParametersBuilder()
            .addString("baseDate", "2026-08-01")
            .toJobParameters()

        assertThatCode { monthlyValidator.validate(jobParameters) }
            .doesNotThrowAnyException()
    }
}
