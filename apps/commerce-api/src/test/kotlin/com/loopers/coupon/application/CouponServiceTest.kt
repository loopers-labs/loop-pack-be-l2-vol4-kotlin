package com.loopers.coupon.application

import com.loopers.account.domain.Account
import com.loopers.account.domain.AccountRole
import com.loopers.account.domain.error.AccountErrorCode
import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.Email
import com.loopers.account.infrastructure.AccountRepository
import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponRepository
import com.loopers.coupon.domain.CouponType
import com.loopers.coupon.domain.UserCoupon
import com.loopers.coupon.domain.UserCouponGrantedType
import com.loopers.coupon.domain.UserCouponRepository
import com.loopers.coupon.domain.UserCouponStatus
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CouponServiceTest {
    private val couponRepository: CouponRepository = mock()
    private val userCouponRepository: UserCouponRepository = mock()
    private val accountRepository: AccountRepository = mock()
    private val service = CouponService(
        couponRepository = couponRepository,
        userCouponRepository = userCouponRepository,
        accountRepository = accountRepository,
    )

    @DisplayName("만료일이 현재보다 과거이면 BAD_REQUEST 예외가 발생하고 저장하지 않는다.")
    @Test
    fun throwsBadRequestAndDoesNotSave_whenExpiredAtIsInPast() {
        val command = createCommand(expiredAt = LocalDateTime.now().minusDays(1))

        val result = assertThrows<BadRequestException> {
            service.create(command)
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(CouponErrorCode.EXPIRED_AT_IN_PAST) },
            { verify(couponRepository, never()).save(any()) },
        )
    }

    @DisplayName("유효한 발행 요청이면 커맨드 값으로 쿠폰을 저장한다.")
    @Test
    fun savesCoupon_whenCommandIsValid() {
        val command = createCommand(
            couponType = CouponType.FIXED,
            value = 2000,
            minOrderAmount = 10000,
            requestAccountId = 7L,
        )
        whenever(couponRepository.save(any())).thenAnswer { it.arguments[0] as Coupon }

        service.create(command)

        val captor = argumentCaptor<Coupon>()
        verify(couponRepository).save(captor.capture())
        val saved = captor.firstValue
        assertAll(
            { assertThat(saved.type).isEqualTo(CouponType.FIXED) },
            { assertThat(saved.value).isEqualTo(2000) },
            { assertThat(saved.minOrderAmount.amount).isEqualTo(10000) },
            { assertThat(saved.createdBy).isEqualTo(7L) },
        )
    }

    @DisplayName("존재하지 않는 쿠폰을 지급하면 NOT_FOUND 예외가 발생하고 저장하지 않는다.")
    @Test
    fun throwsNotFound_whenCouponDoesNotExistForGrant() {
        whenever(couponRepository.findById(COUPON_ID)).thenReturn(null)

        val result = assertThrows<NotFoundException> {
            service.grant(COUPON_ID, USER_ID, ADMIN_ID)
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(CouponErrorCode.COUPON_NOT_FOUND) },
            { verify(userCouponRepository, never()).save(any()) },
        )
    }

    @DisplayName("만료된 쿠폰을 지급하면 BAD_REQUEST 예외가 발생하고 저장하지 않는다.")
    @Test
    fun throwsBadRequest_whenCouponExpiredForGrant() {
        whenever(couponRepository.findById(COUPON_ID)).thenReturn(coupon(expiredAt = LocalDateTime.now().minusDays(1)))

        val result = assertThrows<BadRequestException> {
            service.grant(COUPON_ID, USER_ID, ADMIN_ID)
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(CouponErrorCode.EXPIRED) },
            { verify(userCouponRepository, never()).save(any()) },
        )
    }

    @DisplayName("존재하지 않는 사용자에게 지급하면 NOT_FOUND 예외가 발생하고 저장하지 않는다.")
    @Test
    fun throwsNotFound_whenAccountDoesNotExistForGrant() {
        whenever(couponRepository.findById(COUPON_ID)).thenReturn(coupon())
        whenever(accountRepository.findById(USER_ID)).thenReturn(null)

        val result = assertThrows<NotFoundException> {
            service.grant(COUPON_ID, USER_ID, ADMIN_ID)
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND) },
            { verify(userCouponRepository, never()).save(any()) },
        )
    }

    @DisplayName("이미 지급된 쿠폰을 같은 사용자에게 다시 지급하면 CONFLICT 예외가 발생하고 저장하지 않는다.")
    @Test
    fun throwsConflict_whenCouponAlreadyGranted() {
        whenever(couponRepository.findById(COUPON_ID)).thenReturn(coupon())
        whenever(accountRepository.findById(USER_ID)).thenReturn(account())
        whenever(userCouponRepository.existsByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(true)

        val result = assertThrows<ConflictException> {
            service.grant(COUPON_ID, USER_ID, ADMIN_ID)
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(CouponErrorCode.ALREADY_GRANTED) },
            { verify(userCouponRepository, never()).save(any()) },
        )
    }

    @DisplayName("유효한 지급 요청이면 ADMIN 지급 UserCoupon을 AVAILABLE 상태로 저장한다.")
    @Test
    fun savesUserCoupon_whenGrantRequestIsValid() {
        whenever(couponRepository.findById(COUPON_ID)).thenReturn(coupon())
        whenever(accountRepository.findById(USER_ID)).thenReturn(account())
        whenever(userCouponRepository.existsByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(false)
        whenever(userCouponRepository.save(any())).thenAnswer { it.arguments[0] as UserCoupon }

        service.grant(COUPON_ID, USER_ID, ADMIN_ID)

        val captor = argumentCaptor<UserCoupon>()
        verify(userCouponRepository).save(captor.capture())
        val saved = captor.firstValue
        assertAll(
            { assertThat(saved.userId).isEqualTo(USER_ID) },
            { assertThat(saved.couponId).isEqualTo(COUPON_ID) },
            { assertThat(saved.status).isEqualTo(UserCouponStatus.AVAILABLE) },
            { assertThat(saved.grantedType).isEqualTo(UserCouponGrantedType.ADMIN) },
            { assertThat(saved.grantedBy).isEqualTo(ADMIN_ID) },
        )
    }

    @DisplayName("쿠폰을 사용하면 USED로 전이하고 할인 금액을 돌려준다.")
    @Test
    fun usesCoupon_andReturnsDiscountAmount() {
        whenever(userCouponRepository.findByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(userCoupon())
        whenever(couponRepository.findById(COUPON_ID)).thenReturn(coupon(value = 3000))

        val discount = service.use(USER_ID, COUPON_ID, orderAmount = Money(20_000), now = LocalDateTime.now())

        assertThat(discount).isEqualTo(Money(3000))
    }

    @DisplayName("보유하지 않은 쿠폰을 사용하면 NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenUserCouponMissingForUse() {
        whenever(userCouponRepository.findByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(null)

        val result = assertThrows<NotFoundException> {
            service.use(USER_ID, COUPON_ID, orderAmount = Money(20_000), now = LocalDateTime.now())
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.COUPON_NOT_FOUND)
    }

    @DisplayName("이미 사용한 쿠폰을 다시 사용하면 CONFLICT(ALREADY_USED) 예외가 발생한다.")
    @Test
    fun throwsConflict_whenCouponAlreadyUsedForUse() {
        val used = userCoupon().apply { use(LocalDateTime.now().minusHours(1)) }
        whenever(userCouponRepository.findByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(used)
        whenever(couponRepository.findById(COUPON_ID)).thenReturn(coupon())

        val result = assertThrows<ConflictException> {
            service.use(USER_ID, COUPON_ID, orderAmount = Money(20_000), now = LocalDateTime.now())
        }

        assertThat(result.errorCode).isEqualTo(CouponErrorCode.ALREADY_USED)
    }

    @DisplayName("최소 주문 금액 미달이면 BAD_REQUEST 예외가 발생하고 쿠폰은 사용 처리되지 않는다.")
    @Test
    fun throwsBadRequest_whenMinOrderNotMetForUse() {
        val owned = userCoupon()
        whenever(userCouponRepository.findByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(owned)
        whenever(couponRepository.findById(COUPON_ID)).thenReturn(coupon(minOrderAmount = Money(50_000)))

        val result = assertThrows<BadRequestException> {
            service.use(USER_ID, COUPON_ID, orderAmount = Money(20_000), now = LocalDateTime.now())
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(CouponErrorCode.MIN_ORDER_NOT_MET) },
            { assertThat(owned.status).isEqualTo(UserCouponStatus.AVAILABLE) },
        )
    }

    private fun userCoupon(): UserCoupon = UserCoupon(
        userId = USER_ID,
        couponId = COUPON_ID,
        grantedType = UserCouponGrantedType.ADMIN,
        grantedBy = ADMIN_ID,
    )

    private fun coupon(
        expiredAt: LocalDateTime = LocalDateTime.now().plusDays(1),
        value: Long = 1000,
        minOrderAmount: Money = Money(0),
    ): Coupon = Coupon(
        type = CouponType.FIXED,
        name = "테스트쿠폰",
        value = value,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
        createdBy = ADMIN_ID,
    )

    private fun account(): Account = Account(
        name = AccountName("홍길동"),
        birthDate = LocalDate.of(1996, 1, 1),
        email = Email("user@example.com"),
        role = AccountRole.USER,
    )

    private fun createCommand(
        couponName: String = "테스트쿠폰",
        expiredAt: LocalDateTime = LocalDateTime.now().plusDays(1),
        couponType: CouponType = CouponType.FIXED,
        value: Long = 1000,
        minOrderAmount: Long = 0,
        requestAccountId: Long = 1L,
    ): CouponCreateCommand = CouponCreateCommand(
        couponName = couponName,
        expiredAt = expiredAt,
        couponType = couponType,
        value = value,
        minOrderAmount = minOrderAmount,
        requestAccountId = requestAccountId,
    )

    private companion object {
        private const val COUPON_ID = 1L
        private const val USER_ID = 10L
        private const val ADMIN_ID = 99L
    }
}
