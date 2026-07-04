package com.loopers.domain.payment.port

class PaymentGatewayUnknownException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
