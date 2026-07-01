package com.loopers.application.event

interface UserActivityEvent {
    val userId: Long
    val activityType: String
    val description: String
}
