package com.loopers.infrastructure.coupon

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponTemplateRepositoryPort
import org.springframework.data.domain.PageRequest as SpringPageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class CouponTemplateRepositoryAdapter(
    private val couponTemplateJpaRepository: CouponTemplateJpaRepository,
) : CouponTemplateRepositoryPort {
    override fun save(couponTemplate: CouponTemplate): CouponTemplate =
        couponTemplateJpaRepository.save(CouponTemplateEntity.from(couponTemplate)).toDomain()

    override fun findById(id: Long): CouponTemplate? =
        couponTemplateJpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun findAll(pageRequest: PageRequest): PageResult<CouponTemplate> {
        val springPageable = SpringPageRequest.of(pageRequest.page, pageRequest.size, Sort.by(Sort.Direction.DESC, "id"))
        val page = couponTemplateJpaRepository.findAll(springPageable)
        return PageResult.of(
            items = page.content.map { it.toDomain() },
            pageRequest = pageRequest,
            totalElements = page.totalElements,
        )
    }
}
