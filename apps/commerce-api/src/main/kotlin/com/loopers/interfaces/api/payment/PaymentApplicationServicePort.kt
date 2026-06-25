package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PayCommand
import com.loopers.application.payment.PaymentCallbackCommand
import com.loopers.application.payment.PaymentResult

interface PaymentApplicationServicePort {
    fun pay(command: PayCommand): PaymentResult

    fun handleCallback(command: PaymentCallbackCommand)

    fun reconcile(transactionKey: String): PaymentResult
}
