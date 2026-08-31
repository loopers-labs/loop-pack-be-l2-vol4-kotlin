package com.loopers.infrastructure.payment
import com.loopers.domain.payment.PaymentIntent;import org.springframework.data.jpa.repository.JpaRepository
interface PaymentJpaRepository:JpaRepository<PaymentIntent,Long>{fun findByInternalOrderIdAndPaymentAttemptKey(orderId:Long,attempt:String):PaymentIntent?;fun findByProviderOrderId(orderId:String):PaymentIntent?}
