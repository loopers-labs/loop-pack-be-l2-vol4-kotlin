package com.loopers.domain.queue

import java.time.Duration

interface EntryTokenRepository {
    fun issue(loginId: String, token: String, ttl: Duration)
    fun find(loginId: String): String?
    fun delete(loginId: String)
}
