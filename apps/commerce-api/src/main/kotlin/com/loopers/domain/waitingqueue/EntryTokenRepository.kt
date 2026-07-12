package com.loopers.domain.waitingqueue

import java.time.Duration

interface EntryTokenRepository {
    fun issue(memberId: Long, token: String, ttl: Duration)

    fun find(memberId: Long): String?

    fun delete(memberId: Long)
}
