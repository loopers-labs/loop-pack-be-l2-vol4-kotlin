package com.loopers.domain.coupon.exception

open class CouponDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class InvalidCouponException(
    message: String,
) : CouponDomainException(message)

class CouponNotIssuableException(
    message: String,
) : CouponDomainException(message)

class CouponNotUsableException(
    message: String,
) : CouponDomainException(message)

class CouponNotOwnedException(
    issuedCouponId: Long,
    userId: Long,
) : CouponDomainException("발급 쿠폰 소유자가 아닙니다. issuedCouponId=$issuedCouponId, userId=$userId")

class IssuedCouponNotAvailableException(
    issuedCouponId: Long,
) : CouponDomainException("사용 가능한 발급 쿠폰이 아닙니다. issuedCouponId=$issuedCouponId")
