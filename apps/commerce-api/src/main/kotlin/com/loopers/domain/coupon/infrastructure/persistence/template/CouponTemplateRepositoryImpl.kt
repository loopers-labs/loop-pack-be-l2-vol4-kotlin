package com.loopers.domain.coupon.infrastructure.persistence.template

import com.loopers.domain.coupon.model.CouponTemplateModel
import com.loopers.domain.coupon.port.CouponTemplateRepository
import com.loopers.support.page.PageResult
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

    override fun findByIdOrNull(templateId: Long): CouponTemplateModel? =
        couponTemplateJpaRepository.findById(templateId)
            .map { it.toDomain() }
            .orElse(null)

    override fun findAllByIds(templateIds: Set<Long>): List<CouponTemplateModel> {
        if (templateIds.isEmpty()) {
            return emptyList()
        }
        return couponTemplateJpaRepository.findAllById(templateIds).map { it.toDomain() }
    }

    override fun findAll(page: Int, size: Int): PageResult<CouponTemplateModel> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
        val result = couponTemplateJpaRepository.findAll(pageable)
        return PageResult(
            content = result.content.map { it.toDomain() },
            page = page,
            size = size,
            totalElements = result.totalElements,
        )
    }
}
