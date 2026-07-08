package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
@Transactional(readOnly = true)
class CouponService(
    private val couponRepository: CouponRepository,
) {
    @Transactional
    fun register(
        name: String,
        discountType: DiscountType,
        discountValue: Long,
        minOrderAmount: Long?,
        expiredAt: ZonedDateTime,
        issuableQuantity: Long? = null,
    ): CouponModel {
        val coupon = CouponModel(
            name = name,
            discountType = discountType,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
            issuableQuantity = issuableQuantity,
        )
        return couponRepository.save(coupon)
    }

    @Transactional
    fun tryIssue(couponId: Long): Boolean = couponRepository.tryIssue(couponId)

    fun getById(id: Long): CouponModel {
        return couponRepository.findActiveById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다.")
    }

    fun getAll(pageable: Pageable): Page<CouponModel> = couponRepository.findAllActive(pageable)

    fun findAllByIds(ids: List<Long>): List<CouponModel> = couponRepository.findAllByIds(ids)

    @Transactional
    fun update(
        id: Long,
        name: String,
        discountType: DiscountType,
        discountValue: Long,
        minOrderAmount: Long?,
        expiredAt: ZonedDateTime,
    ) {
        val coupon = getById(id)
        coupon.update(name, discountType, discountValue, minOrderAmount, expiredAt)
    }

    @Transactional
    fun delete(id: Long) {
        val coupon = couponRepository.findActiveById(id) ?: return
        coupon.delete()
    }
}
