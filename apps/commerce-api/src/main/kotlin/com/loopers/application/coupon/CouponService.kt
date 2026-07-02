package com.loopers.application.coupon

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.application.coupon.dto.CouponUpdateCommand
import com.loopers.domain.coupon.CouponPolicy
import com.loopers.domain.coupon.model.Coupon
import com.loopers.domain.coupon.model.CouponIssue
import com.loopers.domain.coupon.repository.CouponIssueRepository
import com.loopers.domain.coupon.repository.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponIssueRepository: CouponIssueRepository,
) {
    @Transactional(readOnly = true)
    fun getCoupon(couponId: Long): Coupon {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Coupon not found.")

        if (coupon.isDeleted) {
            throw CoreException(ErrorType.NOT_FOUND, "Coupon not found.")
        }

        return coupon
    }

    @Transactional(readOnly = true)
    fun getCoupons(page: Int, size: Int): Page<Coupon> {
        return couponRepository.findDisplayable(page = page, size = size)
    }

    @Transactional(readOnly = true)
    fun getCouponIssues(couponId: Long, page: Int, size: Int): Page<CouponIssue> {
        val coupon = getCoupon(couponId)
        return couponIssueRepository.findAllByCouponId(couponId = coupon.id, page = page, size = size)
    }

    @Transactional(readOnly = true)
    fun getCouponIssuesByMemberId(memberId: Long): List<CouponIssue> {
        return couponIssueRepository.findAllByMemberId(memberId)
    }

    @Transactional(readOnly = true)
    fun getCouponIssue(couponIssueId: Long): CouponIssue {
        return couponIssueRepository.findById(couponIssueId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Coupon issue not found.")
    }

    @Transactional
    fun getCouponIssueForUpdate(couponIssueId: Long): CouponIssue {
        return couponIssueRepository.findByIdForUpdate(couponIssueId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Coupon issue not found.")
    }

    fun saveCouponIssue(couponIssue: CouponIssue): CouponIssue {
        return couponIssueRepository.save(couponIssue)
    }

    @Transactional
    fun issueCoupon(memberId: Long, couponId: Long): CouponIssue {
        val coupon = getCoupon(couponId)

        if (!coupon.isValid()) {
            throw CoreException(ErrorType.BAD_REQUEST, "This coupon is not valid. : $couponId")
        }

        return CouponIssue.issue(memberId = memberId, coupon = coupon)
            .let(couponIssueRepository::save)
    }

    @Transactional
    fun createCoupon(command: CouponCreateCommand): Coupon {
        if (couponRepository.existsByName(command.name)) {
            throw CoreException(ErrorType.CONFLICT, "Coupon name already exists.")
        }

        CouponPolicy.validate(
            name = command.name,
            type = command.type,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            issueLimit = command.issueLimit,
            expiredAt = command.expiredAt,
        )

        return Coupon(
            name = command.name,
            type = command.type,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            issueLimit = command.issueLimit,
            expiredAt = command.expiredAt,
        ).let(couponRepository::save)
    }

    @Transactional
    fun updateCoupon(couponId: Long, command: CouponUpdateCommand): Coupon {
        val coupon = getCoupon(couponId)

        if (couponRepository.existsByNameAndIdNot(name = command.name, couponId = coupon.id)) {
            throw CoreException(ErrorType.CONFLICT, "Duplicated coupon name already exists.")
        }

        if (couponIssueRepository.existsByCouponId(coupon.id)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Issued coupon template cannot be updated.")
        }

        CouponPolicy.validate(
            name = command.name,
            type = command.type,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            issueLimit = command.issueLimit,
            expiredAt = command.expiredAt,
        )

        coupon.update(
            name = command.name,
            type = command.type,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            issueLimit = command.issueLimit,
            expiredAt = command.expiredAt,
        )

        return couponRepository.update(coupon)
    }

    @Transactional
    fun deleteCoupon(couponId: Long) {
        val coupon = getCoupon(couponId)
        coupon.delete()
        couponRepository.update(coupon)
    }
}
