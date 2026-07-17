package com.loopers.notification

interface NotificationSender {
    fun notify(title: String, detail: String)
}
