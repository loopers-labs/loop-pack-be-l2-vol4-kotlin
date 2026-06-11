package com.loopers.coupon.application

import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponRepository
import com.loopers.coupon.domain.CouponType
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponService(
    val couponRepository: CouponRepository,
) {
    @Transactional
    fun create(couponCreateCommand: CouponCreateCommand) {
        if (couponCreateCommand.expiredAt < LocalDateTime.now()) {
            throw BadRequestException(CouponErrorCode.EXPIRED_AT_IN_PAST)
        }
        val coupon = Coupon(
            type = couponCreateCommand.couponType,
            name = couponCreateCommand.couponName,
            value = couponCreateCommand.value,
            minOrderAmount = Money(couponCreateCommand.minOrderAmount),
            expiredAt = couponCreateCommand.expiredAt,
            createdBy = couponCreateCommand.requestAccountId,
        )

        couponRepository.save(coupon)
    }
}

data class CouponCreateCommand(
    val couponName: String,
    val expiredAt: LocalDateTime,
    val couponType: CouponType,
    val value: Long,
    val minOrderAmount: Long,
    val requestAccountId: Long,
)
