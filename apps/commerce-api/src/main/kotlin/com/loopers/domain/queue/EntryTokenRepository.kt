package com.loopers.domain.queue

interface EntryTokenRepository {
    fun save(userId: Long, token: EntryToken)

    fun find(userId: Long): EntryToken?

    fun delete(userId: Long)
}
