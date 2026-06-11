package com.loopers.domain.coupon.application.service

import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.exception.CouponDomainException
import com.loopers.domain.coupon.exception.CouponNotIssuableException
import com.loopers.domain.coupon.exception.CouponNotOwnedException
import com.loopers.domain.coupon.exception.CouponNotUsableException
import com.loopers.domain.coupon.model.CouponTemplateModel
import com.loopers.domain.coupon.model.IssuedCouponModel
import com.loopers.domain.coupon.exception.InvalidCouponException
import com.loopers.domain.coupon.exception.IssuedCouponNotAvailableException
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
import org.springframework.dao.DataIntegrityViolationException
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
        runDomain { couponTemplateRepository.save(command.toTemplate()) }

    @Transactional
    fun updateTemplate(templateId: Long, command: CouponTemplateCommand): CouponTemplateModel =
        runDomain {
            val template = findTemplateOrThrow(templateId)
            couponTemplateRepository.save(
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
        runDomain { couponTemplateRepository.save(findTemplateOrThrow(templateId).delete(LocalDateTime.now())) }

    @Transactional
    fun issue(userId: Long, templateId: Long): IssuedCouponModel =
        runDomain {
            val template = findTemplateOrThrow(templateId)
            template.requireIssuable(LocalDateTime.now())
            if (issuedCouponRepository.existsByUserIdAndTemplateId(userId, templateId)) {
                throw CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.")
            }
            issuedCouponRepository.save(IssuedCouponModel.issue(userId, templateId, LocalDateTime.now()))
        }

    @Transactional
    fun validateAndCalculateDiscount(
        userId: Long,
        issuedCouponId: Long,
        totalPrice: Money,
    ): Money =
        runDomain {
            val issuedCoupon = findIssuedCouponForUpdateOrThrow(issuedCouponId)
            issuedCoupon.requireOwnedBy(userId)
            issuedCoupon.requireAvailable()

            val template = findTemplateOrThrow(issuedCoupon.couponTemplateId)
            template.requireUsable(totalPrice, LocalDateTime.now())
            template.calculateDiscount(totalPrice)
        }

    @Transactional
    fun useIssuedCoupon(issuedCouponId: Long): IssuedCouponModel =
        runDomain {
            val issuedCoupon = findIssuedCouponForUpdateOrThrow(issuedCouponId)
            issuedCouponRepository.save(issuedCoupon.use(LocalDateTime.now()))
        }

    @Transactional
    fun cancelUse(issuedCouponId: Long): IssuedCouponModel =
        runDomain {
            val issuedCoupon = findIssuedCouponForUpdateOrThrow(issuedCouponId)
            issuedCouponRepository.save(issuedCoupon.revertUse())
        }

    @Transactional(readOnly = true)
    fun findTemplate(templateId: Long): CouponTemplateModel = findTemplateOrThrow(templateId)

    @Transactional(readOnly = true)
    fun findTemplates(page: Int, size: Int): List<CouponTemplateModel> =
        couponTemplateRepository.findAll(page, size)

    @Transactional(readOnly = true)
    fun findMyCoupons(userId: Long): List<IssuedCouponModel> = issuedCouponRepository.findByUserId(userId)

    @Transactional(readOnly = true)
    fun findIssuedCouponsByTemplate(templateId: Long, page: Int, size: Int): List<IssuedCouponModel> {
        findTemplateOrThrow(templateId)
        return issuedCouponRepository.findByTemplateId(templateId, page, size)
    }

    private fun findTemplateOrThrow(templateId: Long): CouponTemplateModel =
        couponTemplateRepository.findById(templateId) ?: throw CoreException(ErrorType.NOT_FOUND)

    private fun findIssuedCouponForUpdateOrThrow(issuedCouponId: Long): IssuedCouponModel =
        issuedCouponRepository.findByIdForUpdate(issuedCouponId) ?: throw CoreException(ErrorType.NOT_FOUND)

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

    private fun <T> runDomain(block: () -> T): T =
        try {
            block()
        } catch (e: CoreException) {
            throw e
        } catch (e: DataIntegrityViolationException) {
            throw CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.", e)
        } catch (e: InvalidCouponException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        } catch (e: CouponNotIssuableException) {
            throw CoreException(ErrorType.CONFLICT, e.message, e)
        } catch (e: CouponNotUsableException) {
            throw CoreException(ErrorType.CONFLICT, e.message, e)
        } catch (e: CouponNotOwnedException) {
            throw CoreException(ErrorType.CONFLICT, e.message, e)
        } catch (e: IssuedCouponNotAvailableException) {
            throw CoreException(ErrorType.CONFLICT, e.message, e)
        } catch (e: CouponDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
}
