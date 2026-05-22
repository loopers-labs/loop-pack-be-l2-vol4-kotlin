package com.loopers.domain.user

import java.util.concurrent.atomic.AtomicLong

class FakeUserRepository : UserRepository {
    private val store = mutableMapOf<Long, User>()
    private val sequence = AtomicLong(1)

    override fun findByLoginId(loginId: String): User? = store.values.firstOrNull { it.loginId == loginId }

    override fun existsByLoginId(loginId: String): Boolean = store.values.any { it.loginId == loginId }

    override fun findById(id: Long): User? = store[id]

    override fun save(user: User): User {
        val id = idField.getLong(user).takeIf { it != 0L } ?: sequence.getAndIncrement().also { idField.setLong(user, it) }
        store[id] = user
        return user
    }

    companion object {
        private val idField = com.loopers.domain.BaseEntity::class.java.getDeclaredField("id").apply { isAccessible = true }
    }
}
