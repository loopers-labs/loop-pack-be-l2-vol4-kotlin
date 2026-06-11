package com.loopers.domain.coupon.application.service

import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.exception.DuplicateIssuedCouponException
import com.loopers.domain.coupon.model.CouponTemplateModel
import com.loopers.domain.coupon.model.IssuedCouponModel
import com.loopers.domain.coupon.port.CouponTemplateRepository
import com.loopers.domain.coupon.port.IssuedCouponRepository
import com.loopers.domain.coupon.vo.CouponName
import com.loopers.domain.coupon.vo.CouponType
import com.loopers.domain.coupon.vo.DiscountPolicy
import com.loopers.domain.coupon.vo.FixedAmountDiscountPolicy
import com.loopers.domain.coupon.vo.PercentageDiscountPolicy
import com.loopers.domain.product.vo.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class CouponService(
    private val couponTemplateRepository: CouponTemplateRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
) {
    @Transactional
    fun createTemplate(command: CouponTemplateCommand): CouponTemplateModel =
        couponTemplateRepository.save(command.toTemplate())

    @Transactional
    fun updateTemplate(templateId: Long, command: CouponTemplateCommand): CouponTemplateModel {
        val template = getTemplate(templateId)
        return couponTemplateRepository.save(
            template.changePolicy(
                name = CouponName.of(command.name),
                discountPolicy = command.toDiscountPolicy(),
                minOrderAmount = Money.of(command.minOrderAmount),
                expiredAt = command.expiredAt,
            ),
        )
    }

    @Transactional
    fun softDeleteTemplate(templateId: Long): CouponTemplateModel =
        couponTemplateRepository.save(getTemplate(templateId).delete(LocalDateTime.now()))

    @Transactional
    fun issue(userId: Long, templateId: Long): IssuedCouponModel {
        val now = LocalDateTime.now()
        val template = getTemplate(templateId)
        template.requireIssuable(now)
        if (issuedCouponRepository.existsByUserIdAndTemplateId(userId, templateId)) {
            throw DuplicateIssuedCouponException()
        }
        return issuedCouponRepository.save(IssuedCouponModel.issue(userId, templateId, now))
    }

    @Transactional
    fun validateAndCalculateDiscount(
        userId: Long,
        issuedCouponId: Long,
        totalPrice: Money,
    ): Money {
        val issuedCoupon = getIssuedCouponForUpdate(issuedCouponId)
        issuedCoupon.requireOwnedBy(userId)
        issuedCoupon.requireAvailable()

        val template = getTemplate(issuedCoupon.couponTemplateId)
        template.requireUsable(totalPrice, LocalDateTime.now())
        return template.calculateDiscount(totalPrice)
    }

    @Transactional
    fun useIssuedCoupon(issuedCouponId: Long): IssuedCouponModel {
        val issuedCoupon = getIssuedCouponForUpdate(issuedCouponId)
        return issuedCouponRepository.save(issuedCoupon.use(LocalDateTime.now()))
    }

    @Transactional
    fun cancelUse(issuedCouponId: Long): IssuedCouponModel {
        val issuedCoupon = getIssuedCouponForUpdate(issuedCouponId)
        return issuedCouponRepository.save(issuedCoupon.revertUse())
    }

    @Transactional(readOnly = true)
    fun getTemplate(templateId: Long): CouponTemplateModel =
        couponTemplateRepository.findByIdOrNull(templateId) ?: throw CoreException(ErrorType.NOT_FOUND)

    @Transactional(readOnly = true)
    fun getTemplatesByIds(templateIds: Set<Long>): List<CouponTemplateModel> {
        if (templateIds.isEmpty()) {
            return emptyList()
        }
        val templates = couponTemplateRepository.findAllByIds(templateIds)
        if (templates.size != templateIds.size) {
            throw CoreException(ErrorType.NOT_FOUND)
        }
        return templates
    }

    @Transactional(readOnly = true)
    fun findTemplates(page: Int, size: Int): List<CouponTemplateModel> =
        couponTemplateRepository.findAll(page, size)

    @Transactional(readOnly = true)
    fun findMyCoupons(userId: Long): List<IssuedCouponModel> = issuedCouponRepository.findByUserId(userId)

    @Transactional(readOnly = true)
    fun findIssuedCouponsByTemplate(templateId: Long, page: Int, size: Int): List<IssuedCouponModel> {
        getTemplate(templateId)
        return issuedCouponRepository.findByTemplateId(templateId, page, size)
    }

    private fun getIssuedCouponForUpdate(issuedCouponId: Long): IssuedCouponModel =
        issuedCouponRepository.findByIdForUpdateOrNull(issuedCouponId) ?: throw CoreException(ErrorType.NOT_FOUND)

    private fun CouponTemplateCommand.toTemplate(): CouponTemplateModel = CouponTemplateModel(
        name = CouponName.of(name),
        discountPolicy = toDiscountPolicy(),
        minOrderAmount = Money.of(minOrderAmount),
        expiredAt = expiredAt,
    )

    private fun CouponTemplateCommand.toDiscountPolicy(): DiscountPolicy =
        when (CouponType.fromApiType(type)) {
            CouponType.FIXED_AMOUNT -> FixedAmountDiscountPolicy.of(Money.of(value))
            CouponType.PERCENTAGE -> PercentageDiscountPolicy.of(value.toInt())
        }
}
