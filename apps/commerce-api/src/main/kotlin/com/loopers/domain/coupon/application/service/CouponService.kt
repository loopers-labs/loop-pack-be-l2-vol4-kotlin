package com.loopers.domain.coupon.application.service

import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.exception.CouponNotIssuableException
import com.loopers.domain.coupon.exception.DuplicateIssuedCouponException
import com.loopers.domain.coupon.exception.IssuedCouponNotAvailableException
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
import com.loopers.support.page.PageResult
import com.loopers.support.error.ErrorType
import org.springframework.dao.OptimisticLockingFailureException
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
                totalQuantity = command.totalQuantity,
            ),
        )
    }

    @Transactional
    fun softDeleteTemplate(templateId: Long): CouponTemplateModel =
        couponTemplateRepository.save(getTemplate(templateId).delete(LocalDateTime.now()))

    @Transactional(
        noRollbackFor = [
            CouponNotIssuableException::class,
            DuplicateIssuedCouponException::class,
        ],
    )
    fun issue(userId: Long, templateId: Long): IssuedCouponModel {
        val now = LocalDateTime.now()
        val template = getTemplateForUpdate(templateId)
        if (issuedCouponRepository.existsByUserIdAndTemplateId(userId, templateId)) {
            throw DuplicateIssuedCouponException()
        }
        couponTemplateRepository.save(template.increaseIssuedQuantity(now))
        return issuedCouponRepository.save(IssuedCouponModel.issue(userId, templateId, now))
    }

    @Transactional
    fun validateAndCalculateDiscount(
        userId: Long,
        issuedCouponId: Long,
        totalPrice: Money,
    ): Money {
        val issuedCoupon = getIssuedCoupon(issuedCouponId)
        issuedCoupon.requireOwnedBy(userId)
        issuedCoupon.requireAvailable()

        val template = getTemplate(issuedCoupon.couponTemplateId)
        template.requireUsable(totalPrice, LocalDateTime.now())
        return template.calculateDiscount(totalPrice)
    }

    @Transactional
    fun useIssuedCoupon(issuedCouponId: Long): IssuedCouponModel {
        val issuedCoupon = getIssuedCoupon(issuedCouponId)
        return try {
            issuedCouponRepository.save(issuedCoupon.use(LocalDateTime.now()))
        } catch (e: OptimisticLockingFailureException) {
            throw IssuedCouponNotAvailableException()
        }
    }

    @Transactional
    fun cancelUse(issuedCouponId: Long): IssuedCouponModel {
        val issuedCoupon = getIssuedCoupon(issuedCouponId)
        return issuedCouponRepository.save(issuedCoupon.revertUse())
    }

    @Transactional(readOnly = true)
    fun getTemplate(templateId: Long): CouponTemplateModel =
        couponTemplateRepository.findByIdOrNull(templateId) ?: throw CoreException(ErrorType.NOT_FOUND)

    private fun getTemplateForUpdate(templateId: Long): CouponTemplateModel =
        couponTemplateRepository.findByIdForUpdateOrNull(templateId) ?: throw CoreException(ErrorType.NOT_FOUND)

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
    fun findTemplates(page: Int, size: Int): PageResult<CouponTemplateModel> =
        couponTemplateRepository.findAll(page, size)

    @Transactional(readOnly = true)
    fun findMyCoupons(userId: Long): List<IssuedCouponModel> = issuedCouponRepository.findByUserId(userId)

    @Transactional(readOnly = true)
    fun findIssuedCouponsByTemplate(templateId: Long, page: Int, size: Int): PageResult<IssuedCouponModel> {
        getTemplate(templateId)
        return issuedCouponRepository.findByTemplateId(templateId, page, size)
    }

    private fun getIssuedCoupon(issuedCouponId: Long): IssuedCouponModel =
        issuedCouponRepository.findByIdOrNull(issuedCouponId) ?: throw CoreException(ErrorType.NOT_FOUND)

    private fun CouponTemplateCommand.toTemplate(): CouponTemplateModel = CouponTemplateModel(
        name = CouponName.of(name),
        discountPolicy = toDiscountPolicy(),
        minOrderAmount = Money.of(minOrderAmount),
        expiredAt = expiredAt,
        totalQuantity = totalQuantity,
    )

    private fun CouponTemplateCommand.toDiscountPolicy(): DiscountPolicy =
        when (CouponType.fromApiType(type)) {
            CouponType.FIXED_AMOUNT -> FixedAmountDiscountPolicy.of(Money.of(value))
            CouponType.PERCENTAGE -> PercentageDiscountPolicy.of(value.toInt())
        }
}
