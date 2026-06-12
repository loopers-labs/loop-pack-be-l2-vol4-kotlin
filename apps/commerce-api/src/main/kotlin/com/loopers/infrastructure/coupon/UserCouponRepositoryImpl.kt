package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class UserCouponRepositoryImpl(
    private val userCouponJpaRepository: UserCouponJpaRepository,
) : UserCouponRepository {
    // saveAndFlush: 유니크 제약(중복 발급) 위반을 커밋 시점이 아니라 호출 지점에서
    // DataIntegrityViolationException으로 받기 위함 (유스케이스가 CONFLICT로 변환)
    override fun save(userCoupon: UserCouponModel): UserCouponModel {
        return userCouponJpaRepository.saveAndFlush(userCoupon)
    }

    override fun findByIdAndUserId(id: Long, userId: Long): UserCouponModel? {
        return userCouponJpaRepository.findByIdAndUserId(id = id, userId = userId)
    }

    override fun findAllByUserId(userId: Long): List<UserCouponModel> {
        return userCouponJpaRepository.findAllByUserId(userId)
    }

    override fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel> {
        return userCouponJpaRepository.findAllByCouponId(couponId = couponId, pageable = pageable)
    }

    override fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean {
        return userCouponJpaRepository.existsByUserIdAndCouponId(userId = userId, couponId = couponId)
    }
}
