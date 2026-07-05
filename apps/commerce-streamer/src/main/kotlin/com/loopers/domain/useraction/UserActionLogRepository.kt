package com.loopers.domain.useraction

interface UserActionLogRepository {
    fun save(userActionLog: UserActionLog): UserActionLog
}
