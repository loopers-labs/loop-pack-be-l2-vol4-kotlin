package com.loopers.domain.coupon.model

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
    val deletedAtOrNull: LocalDateTime? = null,
) {
    init {
        validateId(id)
    }

    fun calculateDiscount(totalPrice: Money): Money = discountPolicy.calculate(totalPrice)

    fun requireIssuable(now: LocalDateTime) {
        if (deletedAtOrNull != null) {
            throw CouponNotIssuableException("삭제된 쿠폰 템플릿은 발급할 수 없습니다.")
        }
        if (!now.isBefore(expiredAt)) {
            throw CouponNotIssuableException("만료된 쿠폰 템플릿은 발급할 수 없습니다.")
        }
    }

    fun requireUsable(totalPrice: Money, now: LocalDateTime) {
        if (deletedAtOrNull != null) {
            throw CouponNotUsableException("삭제된 쿠폰은 사용할 수 없습니다.")
        }
        if (!now.isBefore(expiredAt)) {
            throw CouponNotUsableException("만료된 쿠폰은 사용할 수 없습니다.")
        }
        if (totalPrice.value < minOrderAmount.value) {
            throw CouponNotUsableException("최소 주문 금액을 만족하지 않습니다.")
        }
    }

    fun changePolicy(
        name: CouponName,
        discountPolicy: DiscountPolicy,
        minOrderAmount: Money,
        expiredAt: LocalDateTime,
    ): CouponTemplateModel = copy(
        name = name,
        discountPolicy = discountPolicy,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
    )

    fun delete(now: LocalDateTime): CouponTemplateModel {
        if (deletedAtOrNull != null) {
            return this
        }
        return copy(deletedAtOrNull = now)
    }

    fun withId(id: Long): CouponTemplateModel {
        validatePersistedId(id)
        return copy(id = id)
    }

    companion object {
        private fun validateId(id: Long) {
            if (id < 0) {
                throw InvalidCouponException("쿠폰 템플릿 ID는 음수일 수 없습니다.")
            }
        }

        private fun validatePersistedId(id: Long) {
            if (id <= 0) {
                throw InvalidCouponException("저장된 쿠폰 템플릿 ID는 양수여야 합니다.")
            }
        }
    }
}
