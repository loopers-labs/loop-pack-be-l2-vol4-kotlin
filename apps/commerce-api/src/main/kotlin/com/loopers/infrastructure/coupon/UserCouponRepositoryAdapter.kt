package com.loopers.infrastructure.coupon

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepositoryPort
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.data.domain.PageRequest as SpringPageRequest

@Component
class UserCouponRepositoryAdapter(
    private val userCouponJpaRepository: UserCouponJpaRepository,
) : UserCouponRepositoryPort {
    override fun save(userCoupon: UserCoupon): UserCoupon =
        userCouponJpaRepository.save(UserCouponEntity.from(userCoupon)).toDomain()

    override fun findById(id: Long): UserCoupon? =
        userCouponJpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean =
        userCouponJpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)

    override fun findAllByUserId(userId: Long): List<UserCoupon> =
        userCouponJpaRepository.findAllByUserIdOrderByIdDesc(userId).map { it.toDomain() }

    override fun findAllByCouponTemplateId(couponTemplateId: Long, pageRequest: PageRequest): PageResult<UserCoupon> {
        val springPageable = SpringPageRequest.of(pageRequest.page, pageRequest.size, Sort.by(Sort.Direction.DESC, "id"))
        val page = userCouponJpaRepository.findAllByCouponTemplateId(couponTemplateId, springPageable)
        return PageResult.of(
            items = page.content.map { it.toDomain() },
            pageRequest = pageRequest,
            totalElements = page.totalElements,
        )
    }
}
