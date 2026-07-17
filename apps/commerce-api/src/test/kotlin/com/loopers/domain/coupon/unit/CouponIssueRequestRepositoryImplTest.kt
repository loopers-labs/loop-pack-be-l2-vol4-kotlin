package com.loopers.domain.coupon.unit

import com.loopers.domain.coupon.infrastructure.persistence.request.CouponIssueRequestJpaRepository
import com.loopers.domain.coupon.infrastructure.persistence.request.CouponIssueRequestRepositoryImpl
import com.loopers.domain.coupon.model.CouponIssueRequestModel
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException

class CouponIssueRequestRepositoryImplTest {
    @Test
    fun `조건부_삽입의_affected_rows를_그대로_반환한다`() {
        val jpaRepository = mockk<CouponIssueRequestJpaRepository>()
        val repository = CouponIssueRequestRepositoryImpl(jpaRepository)
        every { jpaRepository.insertIfAbsent(any(), any(), any(), any(), any()) } returns 0

        assertThat(repository.insertIfAbsent(CouponIssueRequestModel(userId = 7L, couponTemplateId = 42L))).isZero()
    }

    @Test
    fun `조건부_삽입의_무결성_위반은_원본_예외를_전파한다`() {
        val jpaRepository = mockk<CouponIssueRequestJpaRepository>()
        val repository = CouponIssueRequestRepositoryImpl(jpaRepository)
        val exception = DataIntegrityViolationException("other constraint violation")
        every { jpaRepository.insertIfAbsent(any(), any(), any(), any(), any()) } throws exception

        assertThrows<DataIntegrityViolationException> {
            repository.insertIfAbsent(CouponIssueRequestModel(userId = 7L, couponTemplateId = 42L))
        }
    }
}
