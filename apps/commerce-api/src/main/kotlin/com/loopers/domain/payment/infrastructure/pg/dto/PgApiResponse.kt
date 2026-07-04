package com.loopers.domain.payment.infrastructure.pg.dto

data class PgApiResponse<T>(
    val data: T?,
)
