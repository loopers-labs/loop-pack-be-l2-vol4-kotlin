package com.loopers.batch.job.productranking

import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersInvalidException
import org.springframework.batch.core.JobParametersValidator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ProductRankingJobParametersValidator(
    private val period: ProductRankingPeriod,
    private val periodPolicy: ProductRankingPeriodPolicy,
) : JobParametersValidator {
    override fun validate(parameters: JobParameters?) {
        val baseDate = parseBaseDate(parameters)
        runCatching { periodPolicy.calculate(period, baseDate) }
            .onFailure { exception ->
                throw JobParametersInvalidException(exception.message ?: "Invalid baseDate.")
            }
    }

    private fun parseBaseDate(parameters: JobParameters?): LocalDate {
        val value = parameters?.parameters?.get(BASE_DATE_PARAMETER)?.value
            ?: throw JobParametersInvalidException("Required Job parameter baseDate is missing.")

        return when (value) {
            is LocalDate -> value
            is String -> parseBaseDate(value)
            else -> throw JobParametersInvalidException("Job parameter baseDate must use yyyy-MM-dd format.")
        }
    }

    private fun parseBaseDate(value: String): LocalDate {
        if (value.isBlank()) {
            throw JobParametersInvalidException("Required Job parameter baseDate is missing.")
        }
        return try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: DateTimeParseException) {
            throw JobParametersInvalidException("Job parameter baseDate must use yyyy-MM-dd format.")
        }
    }

    companion object {
        const val BASE_DATE_PARAMETER = "baseDate"
    }
}
