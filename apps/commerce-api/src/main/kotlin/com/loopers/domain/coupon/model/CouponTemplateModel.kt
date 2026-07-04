package com.loopers.domain.coupon.model

import com.loopers.domain.coupon.constant.CouponErrorMessages
import com.loopers.domain.coupon.exception.CouponNotIssuableException
import com.loopers.domain.coupon.exception.CouponNotUsableException
import com.loopers.domain.coupon.exception.InvalidCouponException
import com.loopers.domain.coupon.vo.CouponName
import com.loopers.domain.coupon.vo.DiscountPolicy
import com.loopers.domain.product.vo.Money
import java.time.LocalDateTime

data class CouponTemplateModel(
    val id: Long = 0L,
    val name: CouponName,
    val discountPolicy: DiscountPolicy,
    val minOrderAmount: Money,
    val expiredAt: LocalDateTime,
    val totalQuantity: Long = Long.MAX_VALUE,
    val issuedQuantity: Long = 0,
    val deletedAt: LocalDateTime? = null,
) {
    init {
        validateId(id)
    }

    fun calculateDiscount(totalPrice: Money): Money = discountPolicy.calculate(totalPrice)

    fun requireIssuable(now: LocalDateTime) {
        if (deletedAt != null) {
            throw CouponNotIssuableException(CouponErrorMessages.COUPON_TEMPLATE_NOT_ISSUABLE_DELETED)
        }
        if (!now.isBefore(expiredAt)) {
            throw CouponNotIssuableException(CouponErrorMessages.COUPON_TEMPLATE_NOT_ISSUABLE_EXPIRED)
        }
        if (issuedQuantity >= totalQuantity) {
            throw CouponNotIssuableException(CouponErrorMessages.COUPON_TEMPLATE_SOLD_OUT)
        }
    }

    fun requireUsable(totalPrice: Money, now: LocalDateTime) {
        if (deletedAt != null) {
            throw CouponNotUsableException(CouponErrorMessages.COUPON_NOT_USABLE_DELETED)
        }
        if (!now.isBefore(expiredAt)) {
            throw CouponNotUsableException(CouponErrorMessages.COUPON_NOT_USABLE_EXPIRED)
        }
        if (totalPrice < minOrderAmount) {
            throw CouponNotUsableException(CouponErrorMessages.COUPON_NOT_USABLE_MIN_ORDER_AMOUNT)
        }
    }

    fun changePolicy(
        name: CouponName,
        discountPolicy: DiscountPolicy,
        minOrderAmount: Money,
        expiredAt: LocalDateTime,
        totalQuantity: Long = this.totalQuantity,
    ): CouponTemplateModel = copy(
        name = name,
        discountPolicy = discountPolicy,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
        totalQuantity = totalQuantity,
    )

    fun increaseIssuedQuantity(now: LocalDateTime): CouponTemplateModel {
        requireIssuable(now)
        return copy(issuedQuantity = issuedQuantity + 1)
    }

    fun delete(now: LocalDateTime): CouponTemplateModel {
        if (deletedAt != null) {
            return this
        }
        return copy(deletedAt = now)
    }

    fun withId(id: Long): CouponTemplateModel {
        validatePersistedId(id)
        return copy(id = id)
    }

    companion object {
        private fun validateId(id: Long) {
            if (id < 0) {
                throw InvalidCouponException(CouponErrorMessages.COUPON_TEMPLATE_ID_NEGATIVE)
            }
        }

        private fun validatePersistedId(id: Long) {
            if (id <= 0) {
                throw InvalidCouponException(CouponErrorMessages.COUPON_TEMPLATE_PERSISTED_ID_NOT_POSITIVE)
            }
        }
    }
}
