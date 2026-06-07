package com.loopers.domain.coupon

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDateTime

/**
 * 쿠폰 템플릿 도메인 서비스 (Spring 어노테이션 없음).
 * 빈 등록은 application/coupon/CouponConfig 에서 수행한다.
 */
class CouponTemplateService(
    private val couponTemplateRepositoryPort: CouponTemplateRepositoryPort,
) {
    fun getById(id: Long): CouponTemplate =
        couponTemplateRepositoryPort.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다.")

    fun getAll(pageRequest: PageRequest): PageResult<CouponTemplate> =
        couponTemplateRepositoryPort.findAll(pageRequest)

    fun create(
        name: String,
        type: CouponType,
        value: Long,
        minOrderAmount: Long,
        expiredAt: LocalDateTime,
    ): CouponTemplate {
        val couponTemplate = CouponTemplate.create(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
        return couponTemplateRepositoryPort.save(couponTemplate)
    }

    fun update(
        id: Long,
        name: String,
        type: CouponType,
        value: Long,
        minOrderAmount: Long,
        expiredAt: LocalDateTime,
    ): CouponTemplate {
        val existing = getById(id)
        val updated = existing.update(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
        return couponTemplateRepositoryPort.save(updated)
    }
}
