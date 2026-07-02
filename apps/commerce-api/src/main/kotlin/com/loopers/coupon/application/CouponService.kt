package com.loopers.coupon.application

import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.account.infrastructure.AccountRepository
import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponRepository
import com.loopers.coupon.domain.CouponType
import com.loopers.coupon.domain.DiscountPolicy
import com.loopers.coupon.domain.UserCoupon
import com.loopers.coupon.domain.UserCouponGrantedType
import com.loopers.coupon.domain.UserCouponRepository
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponService(
    val couponRepository: CouponRepository,
    val userCouponRepository: UserCouponRepository,
    val accountRepository: AccountRepository,
) {
    @Transactional
    fun create(couponCreateCommand: CouponCreateCommand) {
        if (couponCreateCommand.expiredAt < LocalDateTime.now()) {
            throw BadRequestException(CouponErrorCode.EXPIRED_AT_IN_PAST)
        }
        val coupon = Coupon(
            type = couponCreateCommand.couponType,
            name = couponCreateCommand.couponName,
            value = couponCreateCommand.value,
            minOrderAmount = Money(couponCreateCommand.minOrderAmount),
            expiredAt = couponCreateCommand.expiredAt,
            createdBy = couponCreateCommand.requestAccountId,
            totalQuantity = couponCreateCommand.totalQuantity,
        )

        couponRepository.save(coupon)
    }

    @Transactional
    fun issue(couponId: Long, userId: Long): CouponIssueInfo {
        // 쿠폰 row 배타 락으로 발급 경로 직렬화 — 이후 수량 검증·증가가 lost update 없이 순차 처리된다
        val coupon = couponRepository.findByIdForUpdate(couponId)
            ?: throw NotFoundException(CouponErrorCode.COUPON_NOT_FOUND)
        coupon.issue(LocalDateTime.now())
        // 중복 발급이면 예외로 전체 롤백 → 위에서 증가한 수량도 원복되어 중복 요청이 수량을 소모하지 않는다
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw ConflictException(CouponErrorCode.ALREADY_ISSUED)
        }

        val userCoupon = userCouponRepository.save(
            UserCoupon(
                userId = userId,
                couponId = couponId,
                grantedType = UserCouponGrantedType.FIRST_COME,
                grantedBy = UserCoupon.SYSTEM_GRANTED,
            ),
        )

        return CouponIssueInfo(
            userCouponId = userCoupon.id,
            couponId = coupon.id,
            couponName = coupon.name,
            expiredAt = coupon.expiredAt,
        )
    }

    @Transactional
    fun grant(couponId: Long, userId: Long, grantedBy: Long) {
        val coupon = couponRepository.findById(couponId)
            ?: throw NotFoundException(CouponErrorCode.COUPON_NOT_FOUND)
        if (coupon.isExpired(LocalDateTime.now())) {
            throw BadRequestException(CouponErrorCode.EXPIRED)
        }
        accountRepository.findById(userId)
            ?: throw NotFoundException(AccountErrorCode.ACCOUNT_NOT_FOUND)
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw ConflictException(CouponErrorCode.ALREADY_GRANTED)
        }

        userCouponRepository.save(
            UserCoupon(
                userId = userId,
                couponId = couponId,
                grantedType = UserCouponGrantedType.ADMIN,
                grantedBy = grantedBy,
            ),
        )
    }

    @Transactional
    fun use(userId: Long, couponId: Long, orderAmount: Long, expectedDiscount: Long, now: LocalDateTime): Money {
        val userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
            ?: throw NotFoundException(CouponErrorCode.COUPON_NOT_FOUND)
        val coupon = couponRepository.findById(couponId)
            ?: throw NotFoundException(CouponErrorCode.COUPON_NOT_FOUND)

        coupon.validateUsable(orderAmount, now)
        val discount = DiscountPolicy.calculateDiscount(coupon.type, coupon.value, orderAmount)
        if (discount.amount != expectedDiscount) {
            throw ConflictException(CouponErrorCode.DISCOUNT_NOT_MATCHED)
        }
        userCoupon.use(now)

        return discount
    }

    @Transactional
    fun cancelUse(userId: Long, couponId: Long) {
        val userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
            ?: throw NotFoundException(CouponErrorCode.COUPON_NOT_FOUND)
        userCoupon.cancelUse()
    }
}

data class CouponCreateCommand(
    val couponName: String,
    val expiredAt: LocalDateTime,
    val couponType: CouponType,
    val value: Long,
    val minOrderAmount: Long,
    val requestAccountId: Long,
    val totalQuantity: Long? = null,
)

data class CouponIssueInfo(
    val userCouponId: Long,
    val couponId: Long,
    val couponName: String,
    val expiredAt: LocalDateTime,
)
