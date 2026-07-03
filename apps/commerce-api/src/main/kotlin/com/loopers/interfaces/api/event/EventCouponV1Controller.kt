package com.loopers.interfaces.api.event

import com.loopers.application.event.EventCouponStatus
import com.loopers.application.event.FcfsEventCouponApplicationService
import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.CurrentUser
import com.loopers.support.auth.LoginRequired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@LoginRequired
@RestController
@RequestMapping("/api/v1/events/coupon")
class EventCouponV1Controller(
    private val fcfsEventCouponApplicationService: FcfsEventCouponApplicationService,
) : EventCouponV1ApiSpec {
    @GetMapping("/{couponId}")
    override fun getEventCoupon(
        @CurrentUser user: User,
        @PathVariable couponId: Long,
    ): ApiResponse<EventCouponV1Dto.DetailResponse> =
        fcfsEventCouponApplicationService.get(userId = user.id, couponId = couponId)
            .let(EventCouponV1Dto.DetailResponse::from)
            .let(ApiResponse.Companion::success)

    @PostMapping("/{couponId}")
    override fun requestEventCoupon(
        @CurrentUser user: User,
        @PathVariable couponId: Long,
    ): ResponseEntity<ApiResponse<EventCouponV1Dto.RequestResponse>> {
        val result = fcfsEventCouponApplicationService.request(userId = user.id, couponId = couponId)
        val httpStatus = if (result.status == EventCouponStatus.REQUESTED) HttpStatus.ACCEPTED else HttpStatus.OK
        return ResponseEntity
            .status(httpStatus)
            .body(ApiResponse.success(EventCouponV1Dto.RequestResponse.from(result)))
    }
}
