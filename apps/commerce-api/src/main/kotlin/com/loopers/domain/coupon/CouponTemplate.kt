package com.loopers.domain.coupon

import java.time.LocalDateTime

/**
 * 쿠폰 템플릿 (관리자가 등록하는 쿠폰의 정의).
 * 실제 사용자에게 발급된 쿠폰은 [UserCoupon] 이다.
 */
data class CouponTemplate(
    val id: Long = 0L,
    val name: String,
    val type: CouponType,
    val value: Long,
    val minOrderAmount: Long = 0L,
    val expiredAt: LocalDateTime,
    val totalCount: Long,
) {
    init {
        require(name.isNotBlank()) { "쿠폰 이름은 비어 있을 수 없습니다." }
        require(value > 0) { "쿠폰 할인 값은 0보다 커야 합니다." }
        if (type == CouponType.RATE) {
            require(value in 1..100) { "정률 쿠폰의 할인율은 1~100 사이여야 합니다." }
        }
        require(minOrderAmount >= 0) { "최소 주문 금액은 0 이상이어야 합니다." }
        require(totalCount > 0) { "발급 수량은 0보다 커야 합니다." }
    }

    /**
     * 주문 금액에 대한 할인 금액을 계산한다.
     * - FIXED: min(value, orderAmount)
     * - RATE: orderAmount * value / 100 (원 단위 내림)
     * 할인 금액은 주문 금액을 초과하지 않는다.
     */
    fun calculateDiscount(orderAmount: Long): Long {
        require(orderAmount >= 0) { "주문 금액은 0 이상이어야 합니다." }
        val discount = when (type) {
            CouponType.FIXED -> value
            CouponType.RATE -> orderAmount * value / 100
        }
        return discount.coerceAtMost(orderAmount)
    }

    /**
     * 이 쿠폰을 해당 주문에 적용할 수 있는지(정책)를 판단한다.
     * - 만료되지 않았고
     * - 최소 주문 금액 조건을 만족하는 경우 true.
     */
    fun isApplicableTo(orderAmount: Long, now: LocalDateTime): Boolean {
        if (now.isAfter(expiredAt)) return false
        return orderAmount >= minOrderAmount
    }

    /**
     * 쿠폰 템플릿을 수정한다(전체 교체). 변경된 값으로 불변식을 재검증한다.
     * type 변경(FIXED↔RATE)을 허용하며, value 는 변경된 type 기준으로 검증된다.
     */
    fun update(
        name: String,
        type: CouponType,
        value: Long,
        minOrderAmount: Long,
        expiredAt: LocalDateTime,
    ): CouponTemplate = copy(
        name = name,
        type = type,
        value = value,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
    )

    companion object {
        fun create(
            name: String,
            type: CouponType,
            value: Long,
            minOrderAmount: Long = 0L,
            expiredAt: LocalDateTime,
            totalCount: Long,
        ): CouponTemplate = CouponTemplate(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
            totalCount = totalCount,
        )
    }
}
