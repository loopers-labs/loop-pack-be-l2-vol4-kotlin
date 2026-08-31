package com.loopers.domain.order
import java.time.Instant
interface CouponPolicy { fun discount(buyerId:Long,couponId:Long,originalAmount:Long,requestStartedAt:Instant):Long }
