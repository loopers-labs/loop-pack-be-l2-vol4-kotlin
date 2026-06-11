package com.loopers.domain.coupon.model

import com.loopers.domain.coupon.exception.CouponNotOwnedException
import com.loopers.domain.coupon.exception.InvalidCouponException
import com.loopers.domain.coupon.exception.IssuedCouponNotAvailableException
import java.time.LocalDateTime

enum class IssuedCouponStatus {
    AVAILABLE,
    USED,
}

enum class IssuedCouponDisplayStatus {
    AVAILABLE,
    USED,
    EXPIRED,
}

data class IssuedCouponModel(
    val id: Long = 0L,
    val couponTemplateId: Long,
    val userId: Long,
    val status: IssuedCouponStatus,
    val issuedAt: LocalDateTime,
    val usedAtOrNull: LocalDateTime? = null,
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
            throw CouponNotOwnedException(id, userId)
        }
    }

    fun requireAvailable() {
        if (status != IssuedCouponStatus.AVAILABLE) {
            throw IssuedCouponNotAvailableException(id)
        }
    }

    fun use(now: LocalDateTime): IssuedCouponModel {
        requireAvailable()
        return copy(status = IssuedCouponStatus.USED, usedAtOrNull = now)
    }

    fun revertUse(): IssuedCouponModel {
        if (status == IssuedCouponStatus.AVAILABLE) {
            return this
        }
        return copy(status = IssuedCouponStatus.AVAILABLE, usedAtOrNull = null)
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
                throw InvalidCouponException("발급 쿠폰 ID는 음수일 수 없습니다.")
            }
        }

        private fun validatePersistedId(id: Long) {
            if (id <= 0) {
                throw InvalidCouponException("저장된 발급 쿠폰 ID는 양수여야 합니다.")
            }
        }

        private fun validateCouponTemplateId(couponTemplateId: Long) {
            if (couponTemplateId <= 0) {
                throw InvalidCouponException("쿠폰 템플릿 ID는 양수여야 합니다.")
            }
        }

        private fun validateUserId(userId: Long) {
            if (userId <= 0) {
                throw InvalidCouponException("사용자 ID는 양수여야 합니다.")
            }
        }
    }
}
