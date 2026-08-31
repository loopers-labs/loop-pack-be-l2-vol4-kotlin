package com.loopers.domain.order
import org.springframework.stereotype.Component
import java.time.Instant
// Hides: ownership, policy evaluation, and same-coupon snapshot reuse.
@Component class OrderDiscountService(private val policy:CouponPolicy){
 fun apply(order:Order,buyerId:Long,couponId:Long,startedAt:Instant){order.requireOwner(buyerId);if(order.appliedCouponId==couponId)return;order.applyDiscount(couponId,policy.discount(buyerId,couponId,order.originalAmount,startedAt),startedAt)}
}
