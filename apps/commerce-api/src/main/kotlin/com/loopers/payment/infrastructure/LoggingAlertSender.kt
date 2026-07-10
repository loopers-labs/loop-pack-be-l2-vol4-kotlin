package com.loopers.payment.infrastructure

import com.loopers.payment.application.AlertSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoggingAlertSender : AlertSender {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun alert(message: String) {
        logger.error("[PAYMENT-ALERT] {}", message)
    }
}
