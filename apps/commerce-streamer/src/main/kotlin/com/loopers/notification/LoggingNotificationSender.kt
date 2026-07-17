package com.loopers.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoggingNotificationSender : NotificationSender {
    private val logger = LoggerFactory.getLogger(LoggingNotificationSender::class.java)

    override fun notify(title: String, detail: String) {
        logger.error("[ALERT] {} — {}", title, detail)
    }
}
