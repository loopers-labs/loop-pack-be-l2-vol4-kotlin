package com.loopers.domain.coupon.infrastructure.persistence.template

import com.loopers.domain.coupon.model.CouponTemplateModel
import com.loopers.domain.coupon.port.CouponTemplateRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class CouponTemplateRepositoryImpl(
    private val couponTemplateJpaRepository: CouponTemplateJpaRepository,
) : CouponTemplateRepository {
    override fun save(template: CouponTemplateModel): CouponTemplateModel {
        val entity = if (template.id == 0L) {
            CouponTemplateJpaEntity.fromDomain(template)
        } else {
            couponTemplateJpaRepository.findById(template.id).orElseThrow()
                .also { it.updateFrom(template) }
        }
        return couponTemplateJpaRepository.saveAndFlush(entity).toDomain()
    }

    override fun findById(templateId: Long): CouponTemplateModel? =
        couponTemplateJpaRepository.findById(templateId)
            .map { it.toDomain() }
            .orElse(null)

    override fun findAll(page: Int, size: Int): List<CouponTemplateModel> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        return couponTemplateJpaRepository.findAll(pageable).content.map { it.toDomain() }
    }
}
