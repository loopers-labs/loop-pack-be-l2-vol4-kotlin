package com.loopers.domain.payment
interface PaymentIntentRepository{fun save(p:PaymentIntent):PaymentIntent;fun find(id:Long):PaymentIntent?;fun findByAttempt(orderId:Long,attempt:String):PaymentIntent?;fun findByProviderOrderId(orderId:String):PaymentIntent?}
