package com.loopers.domain.coupon.unit

import com.loopers.domain.coupon.exception.DuplicateIssuedCouponException
import com.loopers.domain.coupon.infrastructure.persistence.issued.ISSUED_COUPON_USER_TEMPLATE_UNIQUE_CONSTRAINT
import com.loopers.domain.coupon.infrastructure.persistence.issued.IssuedCouponJpaRepository
import com.loopers.domain.coupon.infrastructure.persistence.issued.IssuedCouponRepositoryImpl
import com.loopers.domain.coupon.model.IssuedCouponModel
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime

class IssuedCouponRepositoryImplTest {
    @Test
    fun `user_template_유니크_제약_위반은_중복_발급_예외로_변환한다`() {
        val issuedCouponJpaRepository = mockk<IssuedCouponJpaRepository>()
        val issuedCouponRepository = IssuedCouponRepositoryImpl(issuedCouponJpaRepository)
        every { issuedCouponJpaRepository.saveAndFlush(any()) } throws
            DataIntegrityViolationException(
                "Duplicate entry for key '$ISSUED_COUPON_USER_TEMPLATE_UNIQUE_CONSTRAINT'",
            )

        assertThrows<DuplicateIssuedCouponException> {
            issuedCouponRepository.save(발급쿠폰_생성())
        }
    }

    @Test
    fun `user_template_유니크_제약이_아닌_무결성_위반은_원본_예외를_전파한다`() {
        val issuedCouponJpaRepository = mockk<IssuedCouponJpaRepository>()
        val issuedCouponRepository = IssuedCouponRepositoryImpl(issuedCouponJpaRepository)
        val exception = DataIntegrityViolationException("other constraint violation")
        every { issuedCouponJpaRepository.saveAndFlush(any()) } throws exception

        assertThrows<DataIntegrityViolationException> {
            issuedCouponRepository.save(발급쿠폰_생성())
        }
    }

    private fun 발급쿠폰_생성(): IssuedCouponModel = IssuedCouponModel.issue(
        userId = 1L,
        couponTemplateId = 10L,
        now = LocalDateTime.now(),
    )
}
