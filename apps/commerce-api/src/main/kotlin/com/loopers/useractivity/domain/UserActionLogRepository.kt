package com.loopers.useractivity.domain

interface UserActionLogRepository {
    fun append(userActionLog: UserActionLog): UserActionLog
}
