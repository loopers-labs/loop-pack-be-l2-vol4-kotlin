package com.loopers.domain.coupon.exception

open class CouponDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class InvalidCouponException(
    message: String,
) : CouponDomainException(message)

open class CouponConflictException(
    message: String,
    cause: Throwable? = null,
) : CouponDomainException(message, cause)

class CouponNotIssuableException(
    message: String,
) : CouponConflictException(message)

class CouponNotUsableException(
    message: String,
) : CouponConflictException(message)

class CouponNotOwnedException : CouponDomainException("발급 쿠폰을 찾을 수 없습니다.")

class IssuedCouponNotAvailableException : CouponConflictException("사용 가능한 발급 쿠폰이 아닙니다.")

class DuplicateIssuedCouponException(
    cause: Throwable? = null,
) : CouponConflictException("이미 발급받은 쿠폰입니다.", cause)
