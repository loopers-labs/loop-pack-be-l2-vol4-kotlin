package com.loopers.infrastructure.payment
import com.loopers.domain.payment.*;import org.springframework.stereotype.Component
@Component class PaymentIntentRepositoryImpl(private val jpa:PaymentJpaRepository):PaymentIntentRepository{override fun save(p:PaymentIntent)=jpa.save(p);override fun find(id:Long)=jpa.findById(id).orElse(null);override fun findByAttempt(orderId:Long,attempt:String)=jpa.findByInternalOrderIdAndPaymentAttemptKey(orderId,attempt);override fun findByProviderOrderId(orderId:String)=jpa.findByProviderOrderId(orderId)}
