package com.loopers.domain.coupon.unit

import com.loopers.domain.coupon.application.service.CouponIssueRequestService
import com.loopers.domain.coupon.model.CouponIssueRequestModel
import com.loopers.domain.coupon.port.CouponIssueRequestRepository
import com.loopers.domain.coupon.port.CouponTemplateRepository
import com.loopers.domain.coupon.support.CouponSteps.Companion.쿠폰템플릿_도메인_생성
import com.loopers.support.event.CouponIssueRequestedApplicationEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException

class CouponIssueRequestServiceTest {
    @Test
    fun `발급_요청의_partitionKey는_requestId가_아닌_couponTemplateId다`() {
        val couponTemplateRepository = mockk<CouponTemplateRepository>()
        val couponIssueRequestRepository = mockk<CouponIssueRequestRepository>()
        val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val event = slot<CouponIssueRequestedApplicationEvent>()
        val template = 쿠폰템플릿_도메인_생성(id = 42L)
        every { couponTemplateRepository.findByIdOrNull(template.id) } returns template
        val saved = CouponIssueRequestModel(userId = 7L, couponTemplateId = template.id).copy(id = 91L)
        every {
            couponIssueRequestRepository.findByUserIdAndCouponTemplateIdOrNull(7L, template.id)
        } returnsMany listOf(null, saved)
        every { couponIssueRequestRepository.insertIfAbsent(any()) } returns 1
        val service = CouponIssueRequestService(
            couponTemplateRepository = couponTemplateRepository,
            couponIssueRequestRepository = couponIssueRequestRepository,
            applicationEventPublisher = applicationEventPublisher,
        )

        val request = service.requestIssue(userId = 7L, couponTemplateId = template.id)

        verify(exactly = 1) { applicationEventPublisher.publishEvent(capture(event)) }
        assertThat(event.captured.requestId).isEqualTo(request.requestId)
        assertThat(event.captured.requestAggregateId).isEqualTo(request.id)
        assertThat(event.captured.userId).isEqualTo(7L)
        assertThat(event.captured.couponTemplateId).isEqualTo(template.id)
        assertThat(event.captured.occurredAt).isNotNull()
        verify(exactly = 0) { couponTemplateRepository.findByIdForUpdateOrNull(any()) }
    }

    @Test
    fun `중복_발급_요청은_기존_request를_반환하고_이벤트를_발행하지_않는다`() {
        val couponTemplateRepository = mockk<CouponTemplateRepository>()
        val couponIssueRequestRepository = mockk<CouponIssueRequestRepository>()
        val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val template = 쿠폰템플릿_도메인_생성(id = 42L)
        val existing = CouponIssueRequestModel(userId = 7L, couponTemplateId = template.id).copy(id = 91L)
        every { couponTemplateRepository.findByIdOrNull(template.id) } returns template
        every { couponIssueRequestRepository.findByUserIdAndCouponTemplateIdOrNull(7L, template.id) } returns existing
        val service = CouponIssueRequestService(
            couponTemplateRepository = couponTemplateRepository,
            couponIssueRequestRepository = couponIssueRequestRepository,
            applicationEventPublisher = applicationEventPublisher,
        )

        val duplicate = service.requestIssue(userId = 7L, couponTemplateId = template.id)

        assertThat(duplicate).isEqualTo(existing)
        verify(exactly = 0) { couponIssueRequestRepository.insertIfAbsent(any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any()) }
        verify(exactly = 0) { couponTemplateRepository.findByIdForUpdateOrNull(any()) }
    }

    @Test
    fun `동시_요청의_user_template_유니크_충돌은_기존_request로_수렴하고_이벤트를_발행하지_않는다`() {
        val couponTemplateRepository = mockk<CouponTemplateRepository>()
        val couponIssueRequestRepository = mockk<CouponIssueRequestRepository>()
        val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val template = 쿠폰템플릿_도메인_생성(id = 42L)
        val existing = CouponIssueRequestModel(userId = 7L, couponTemplateId = template.id).copy(id = 91L)
        every { couponTemplateRepository.findByIdOrNull(template.id) } returns template
        every {
            couponIssueRequestRepository.findByUserIdAndCouponTemplateIdOrNull(7L, template.id)
        } returnsMany listOf(null, existing)
        every { couponIssueRequestRepository.insertIfAbsent(any()) } returns 0
        val service = CouponIssueRequestService(
            couponTemplateRepository = couponTemplateRepository,
            couponIssueRequestRepository = couponIssueRequestRepository,
            applicationEventPublisher = applicationEventPublisher,
        )

        val converged = service.requestIssue(userId = 7L, couponTemplateId = template.id)

        assertThat(converged).isEqualTo(existing)
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any()) }
        verify(exactly = 0) { couponTemplateRepository.findByIdForUpdateOrNull(any()) }
    }

    @Test
    fun `조건부_삽입이_충돌했지만_user_template_request가_없으면_무결성_예외를_전파한다`() {
        val couponTemplateRepository = mockk<CouponTemplateRepository>()
        val couponIssueRequestRepository = mockk<CouponIssueRequestRepository>()
        val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val template = 쿠폰템플릿_도메인_생성(id = 42L)
        every { couponTemplateRepository.findByIdOrNull(template.id) } returns template
        every { couponIssueRequestRepository.findByUserIdAndCouponTemplateIdOrNull(7L, template.id) } returns null
        every { couponIssueRequestRepository.insertIfAbsent(any()) } returns 0
        val service = CouponIssueRequestService(
            couponTemplateRepository = couponTemplateRepository,
            couponIssueRequestRepository = couponIssueRequestRepository,
            applicationEventPublisher = applicationEventPublisher,
        )

        assertThrows<DataIntegrityViolationException> {
            service.requestIssue(userId = 7L, couponTemplateId = template.id)
        }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any()) }
    }
}
