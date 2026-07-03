package com.loopers.application.coupon.usecase

import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueRequestedEvent
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

@Component
class RequestCouponIssueUsecase(
    private val userService: UserService,
    private val couponRepository: CouponRepository,
    private val requestRepository: CouponIssueRequestRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    // 접수만: 쿠폰 유효성 확인 후 PENDING 요청 저장 + Outbox 발행(같은 tx). 발급은 Consumer가 비동기 수행.
    @Transactional
    fun execute(loginId: String, password: String, couponId: Long): String {
        val user = userService.getProfile(loginId = loginId, password = password)
        val coupon = couponRepository.findActiveById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        if (coupon.isExpired(ZonedDateTime.now())) {
            throw CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급받을 수 없습니다.")
        }
        val requestId = UUID.randomUUID().toString()
        requestRepository.save(CouponIssueRequest(requestId = requestId, userId = user.id, couponId = couponId))
        eventPublisher.publishEvent(CouponIssueRequestedEvent(requestId = requestId, userId = user.id, couponId = couponId))
        return requestId
    }
}
