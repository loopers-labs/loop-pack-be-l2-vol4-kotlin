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

    /** 주어진 id 집합에 해당하는 템플릿을 id → 템플릿 Map 으로 반환한다(삭제/미존재 id는 제외). */
    fun getByIds(ids: Set<Long>): Map<Long, CouponTemplate> =
        couponTemplateRepositoryPort.findByIds(ids).associateBy { it.id }

    fun create(
        name: String,
        type: CouponType,
        value: Long,
        minOrderAmount: Long,
        expiredAt: LocalDateTime,
        totalCount: Long,
    ): CouponTemplate {
        val couponTemplate = CouponTemplate.create(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
            totalCount = totalCount,
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

    fun delete(id: Long) {
        val deletedCount = couponTemplateRepositoryPort.delete(id)
        if (deletedCount == 0) {
            throw CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다.")
        }
    }
}
