package com.loopers.domain.coupon.model

import com.loopers.domain.coupon.constant.CouponErrorMessages
import com.loopers.domain.coupon.exception.CouponNotOwnedException
import com.loopers.domain.coupon.exception.InvalidCouponException
import com.loopers.domain.coupon.exception.IssuedCouponNotAvailableException
import java.time.LocalDateTime

data class IssuedCouponModel(
    val id: Long = 0L,
    val couponTemplateId: Long,
    val userId: Long,
    val status: IssuedCouponStatus,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime? = null,
) {
    init {
        validateId(id)
        validateCouponTemplateId(couponTemplateId)
        validateUserId(userId)
    }

    fun withId(id: Long): IssuedCouponModel {
        validatePersistedId(id)
        return copy(id = id)
    }

    fun requireOwnedBy(userId: Long) {
        validateUserId(userId)
        if (this.userId != userId) {
            throw CouponNotOwnedException()
        }
    }

    fun requireAvailable() {
        if (status != IssuedCouponStatus.AVAILABLE) {
            throw IssuedCouponNotAvailableException()
        }
    }

    fun use(now: LocalDateTime): IssuedCouponModel {
        requireAvailable()
        return copy(status = IssuedCouponStatus.USED, usedAt = now)
    }

    fun revertUse(): IssuedCouponModel {
        if (status == IssuedCouponStatus.AVAILABLE) {
            return this
        }
        return copy(status = IssuedCouponStatus.AVAILABLE, usedAt = null)
    }

    fun displayStatus(expiredAt: LocalDateTime, now: LocalDateTime): IssuedCouponDisplayStatus =
        when {
            status == IssuedCouponStatus.USED -> IssuedCouponDisplayStatus.USED
            !now.isBefore(expiredAt) -> IssuedCouponDisplayStatus.EXPIRED
            else -> IssuedCouponDisplayStatus.AVAILABLE
        }

    companion object {
        fun issue(
            userId: Long,
            couponTemplateId: Long,
            now: LocalDateTime,
        ): IssuedCouponModel = IssuedCouponModel(
            couponTemplateId = couponTemplateId,
            userId = userId,
            status = IssuedCouponStatus.AVAILABLE,
            issuedAt = now,
        )

        private fun validateId(id: Long) {
            if (id < 0) {
                throw InvalidCouponException(CouponErrorMessages.ISSUED_COUPON_ID_NEGATIVE)
            }
        }

        private fun validatePersistedId(id: Long) {
            if (id <= 0) {
                throw InvalidCouponException(CouponErrorMessages.ISSUED_COUPON_PERSISTED_ID_NOT_POSITIVE)
            }
        }

        private fun validateCouponTemplateId(couponTemplateId: Long) {
            if (couponTemplateId <= 0) {
                throw InvalidCouponException(CouponErrorMessages.COUPON_TEMPLATE_ID_NOT_POSITIVE)
            }
        }

        private fun validateUserId(userId: Long) {
            if (userId <= 0) {
                throw InvalidCouponException(CouponErrorMessages.USER_ID_NOT_POSITIVE)
            }
        }
    }
}
