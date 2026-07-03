package com.loopers.interfaces.api.event

import com.loopers.domain.user.User
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity

@Tag(name = "Event Coupon V1 API", description = "Loopers first come first served event coupon API.")
interface EventCouponV1ApiSpec {
    @Operation(summary = "이벤트 쿠폰 조회", description = "선착순 이벤트 쿠폰 정보와 현재 사용자의 상태를 조회합니다.")
    fun getEventCoupon(user: User, couponId: Long): ApiResponse<EventCouponV1Dto.DetailResponse>

    @Operation(summary = "이벤트 쿠폰 발급 요청", description = "선착순 이벤트 쿠폰 발급을 비동기로 요청합니다.")
    fun requestEventCoupon(user: User, couponId: Long): ResponseEntity<ApiResponse<EventCouponV1Dto.RequestResponse>>
}
