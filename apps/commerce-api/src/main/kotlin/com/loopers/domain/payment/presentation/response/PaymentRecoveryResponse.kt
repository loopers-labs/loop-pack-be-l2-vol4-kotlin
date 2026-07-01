package com.loopers.domain.payment.presentation.response

import com.loopers.domain.payment.application.info.PaymentRecoveryResult

data class PaymentRecoveryResponse(
    val scanned: Int,
    val recovered: Int,
) {
    companion object {
        fun from(result: PaymentRecoveryResult): PaymentRecoveryResponse = PaymentRecoveryResponse(
            scanned = result.scanned,
            recovered = result.recovered,
        )
    }
}
