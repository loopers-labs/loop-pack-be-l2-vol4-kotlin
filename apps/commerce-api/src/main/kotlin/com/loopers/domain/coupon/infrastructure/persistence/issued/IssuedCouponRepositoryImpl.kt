package com.loopers.domain.coupon.infrastructure.persistence.issued

import com.loopers.domain.coupon.model.IssuedCouponModel
import com.loopers.domain.coupon.port.IssuedCouponRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class IssuedCouponRepositoryImpl(
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
) : IssuedCouponRepository {
    override fun save(issuedCoupon: IssuedCouponModel): IssuedCouponModel {
        val entity = if (issuedCoupon.id == 0L) {
            IssuedCouponJpaEntity.fromDomain(issuedCoupon)
        } else {
            issuedCouponJpaRepository.findById(issuedCoupon.id).orElseThrow()
                .also { it.updateFrom(issuedCoupon) }
        }
        return issuedCouponJpaRepository.saveAndFlush(entity).toDomain()
    }

    override fun existsByUserIdAndTemplateId(userId: Long, templateId: Long): Boolean =
        issuedCouponJpaRepository.existsByUserIdAndCouponTemplateId(userId, templateId)

    override fun findByIdForUpdate(issuedCouponId: Long): IssuedCouponModel? =
        issuedCouponJpaRepository.findByIdForUpdate(issuedCouponId)?.toDomain()

    override fun findByUserId(userId: Long): List<IssuedCouponModel> =
        issuedCouponJpaRepository.findByUserIdOrderByIssuedAtDesc(userId).map { it.toDomain() }

    override fun findByTemplateId(templateId: Long, page: Int, size: Int): List<IssuedCouponModel> =
        issuedCouponJpaRepository
            .findByCouponTemplateIdOrderByIssuedAtDesc(templateId, PageRequest.of(page, size))
            .map { it.toDomain() }
}
