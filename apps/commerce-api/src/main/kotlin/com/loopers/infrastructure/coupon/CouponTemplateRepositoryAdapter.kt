package com.loopers.infrastructure.coupon

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponTemplateRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.PageRequest as SpringPageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class CouponTemplateRepositoryAdapter(
    private val couponTemplateJpaRepository: CouponTemplateJpaRepository,
) : CouponTemplateRepositoryPort {
    override fun save(couponTemplate: CouponTemplate): CouponTemplate {
        val entity = if (couponTemplate.id == 0L) {
            CouponTemplateEntity.from(couponTemplate)
        } else {
            couponTemplateJpaRepository.findById(couponTemplate.id)
                .orElseThrow { CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다.") }
                .apply {
                    update(
                        name = couponTemplate.name,
                        type = PersistedCouponType.from(couponTemplate.type),
                        discountValue = couponTemplate.value,
                        minOrderAmount = couponTemplate.minOrderAmount,
                        expiredAt = couponTemplate.expiredAt,
                    )
                }
        }
        return couponTemplateJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): CouponTemplate? =
        couponTemplateJpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun findByIds(ids: Set<Long>): List<CouponTemplate> {
        if (ids.isEmpty()) return emptyList()
        return couponTemplateJpaRepository.findAllById(ids).map { it.toDomain() }
    }

    override fun delete(id: Long): Int =
        couponTemplateJpaRepository.softDeleteById(id, ZonedDateTime.now())

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
