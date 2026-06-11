package com.loopers.domain.coupon.presentation.request

import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.time.LocalDateTime

data class CouponTemplateRequest(
    @field:NotBlank
    val name: String,
    @field:NotBlank
    val type: String,
    @field:Positive
    val value: Long,
    @field:PositiveOrZero
    val minOrderAmount: Long = 0L,
    @field:Future
    val expiredAt: LocalDateTime,
) {
    fun toCommand(): CouponTemplateCommand = CouponTemplateCommand(
        name = name,
        type = type,
        value = value,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
    )
}
