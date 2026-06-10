package com.loopers.infrastructure.like

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class LikeCommandRepositoryImpl(
    private val entityManager: EntityManager,
) : LikeCommandRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun createIfAbsent(userId: Long, productId: Long): Int {
        return entityManager.createNativeQuery(
            """
            insert ignore into likes (
                user_id,
                product_id,
                created_at,
                updated_at
            )
            values (
                :userId,
                :productId,
                current_timestamp,
                current_timestamp
            )
            """.trimIndent(),
        )
            .setParameter("userId", userId)
            .setParameter("productId", productId)
            .executeUpdate()
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun restoreIfCanceled(userId: Long, productId: Long): Int {
        return entityManager.createNativeQuery(
            """
            update likes
            set deleted_at = null,
                updated_at = current_timestamp
            where user_id = :userId
              and product_id = :productId
              and deleted_at is not null
            """.trimIndent(),
        )
            .setParameter("userId", userId)
            .setParameter("productId", productId)
            .executeUpdate()
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun cancelIfActive(userId: Long, productId: Long): Int {
        return entityManager.createNativeQuery(
            """
            update likes
            set deleted_at = current_timestamp,
                updated_at = current_timestamp
            where user_id = :userId
              and product_id = :productId
              and deleted_at is null
            """.trimIndent(),
        )
            .setParameter("userId", userId)
            .setParameter("productId", productId)
            .executeUpdate()
    }
}
