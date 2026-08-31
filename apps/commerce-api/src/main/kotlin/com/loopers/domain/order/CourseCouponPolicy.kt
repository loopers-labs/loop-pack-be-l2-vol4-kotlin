package com.loopers.domain.order
import com.loopers.support.error.*
import org.springframework.stereotype.Component
import java.time.Instant
// Hides: the learner fixture's coupon ownership, rate, and request-start expiry decision.
@Component class CourseCouponPolicy:CouponPolicy {
 override fun discount(buyerId:Long,couponId:Long,originalAmount:Long,requestStartedAt:Instant):Long {
  if(buyerId<=0||couponId!=10L||!requestStartedAt.isBefore(EXPIRES_AT)) throw CoreException(ErrorType.BAD_REQUEST,"coupon unavailable")
  return originalAmount/10
 }
 companion object { val EXPIRES_AT:Instant=Instant.parse("2026-09-01T00:00:00Z") }
}
