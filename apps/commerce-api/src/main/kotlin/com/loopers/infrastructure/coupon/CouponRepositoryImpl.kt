package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: CouponModel): CouponModel = couponJpaRepository.save(coupon)

    override fun findActiveById(id: Long): CouponModel? = couponJpaRepository.findActiveById(id)

    override fun findAllActive(pageable: Pageable): Page<CouponModel> = couponJpaRepository.findAllActive(pageable)

    override fun findAllByIds(ids: List<Long>): List<CouponModel> =
        if (ids.isEmpty()) emptyList() else couponJpaRepository.findAllById(ids).toList()

    override fun tryIssue(couponId: Long): Boolean = couponJpaRepository.tryIssue(couponId) == 1
}
