package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueResult
import com.loopers.domain.coupon.CouponIssueResultRepository
import com.loopers.domain.coupon.CouponIssueStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class CouponIssueResultRepositoryImpl(
    private val couponIssueResultJpaRepository: CouponIssueResultJpaRepository,
) : CouponIssueResultRepository {
    override fun save(result: CouponIssueResult): CouponIssueResult {
        val entity = result.id
            ?.let { couponIssueResultJpaRepository.findByIdOrNull(it) }
            ?: CouponIssueResultJpaEntity.from(result)

        entity.updateFrom(result)
        return couponIssueResultJpaRepository.save(entity).toDomain()
    }

    override fun findByRequestId(requestId: String): CouponIssueResult? {
        return couponIssueResultJpaRepository.findByRequestId(requestId)?.toDomain()
    }

    override fun existsSuccess(userId: Long, couponId: Long): Boolean {
        return couponIssueResultJpaRepository.existsByUserIdAndCouponIdAndStatus(
            userId = userId,
            couponId = couponId,
            status = CouponIssueStatus.SUCCESS,
        )
    }
}
