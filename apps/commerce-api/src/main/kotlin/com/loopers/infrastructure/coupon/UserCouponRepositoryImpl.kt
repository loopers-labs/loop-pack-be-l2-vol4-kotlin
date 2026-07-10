package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class UserCouponRepositoryImpl(
    private val userCouponJpaRepository: UserCouponJpaRepository,
) : UserCouponRepository {
    override fun save(userCoupon: UserCoupon): UserCoupon {
        return userCouponJpaRepository.save(UserCouponJpaEntity.from(userCoupon)).toDomain()
    }

    override fun saveIssued(userCoupon: UserCoupon): UserCoupon {
        return userCouponJpaRepository.saveAndFlush(UserCouponJpaEntity.from(userCoupon)).toDomain()
    }

    override fun findById(id: Long): UserCoupon? {
        return userCouponJpaRepository.findByIdAndDeletedAtIsNull(id)?.toDomain()
    }

    override fun useIfNotUsed(id: Long, userId: Long, usedAt: LocalDateTime): Boolean {
        return userCouponJpaRepository.useIfNotUsed(id = id, userId = userId, usedAt = usedAt) == 1
    }

    override fun cancelUseIfUsed(id: Long, userId: Long): Boolean {
        return userCouponJpaRepository.cancelUseIfUsed(id = id, userId = userId) == 1
    }
}
