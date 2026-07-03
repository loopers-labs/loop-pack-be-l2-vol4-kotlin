package com.loopers.domain.payment.unit

import org.springframework.http.HttpStatus

data class PgResponse(
    val status: HttpStatus = HttpStatus.OK,
)
