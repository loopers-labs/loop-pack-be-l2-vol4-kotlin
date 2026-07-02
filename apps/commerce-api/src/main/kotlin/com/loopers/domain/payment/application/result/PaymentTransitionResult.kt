package com.loopers.domain.payment.application.result

import com.loopers.domain.payment.model.PaymentModel

data class PaymentTransitionResult(val payment: PaymentModel, val changed: Boolean)
