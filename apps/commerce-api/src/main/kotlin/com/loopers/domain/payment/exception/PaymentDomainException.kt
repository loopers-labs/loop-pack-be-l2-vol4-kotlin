package com.loopers.domain.payment.exception

open class PaymentDomainException(message: String) : RuntimeException(message)

class InvalidPaymentException(message: String) : PaymentDomainException(message)
