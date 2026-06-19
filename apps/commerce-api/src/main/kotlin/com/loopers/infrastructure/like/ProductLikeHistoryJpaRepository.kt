package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLikeHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductLikeHistoryJpaRepository : JpaRepository<ProductLikeHistory, Long> {
    fun findTopByUserIdAndProductIdOrderByCreatedAtDescIdDesc(userId: Long, productId: Long): ProductLikeHistory?

    @Query(
        value = """
            select latest.product_id
            from (
                select h.product_id,
                       h.action,
                       row_number() over (
                           partition by h.user_id, h.product_id
                           order by h.created_at desc, h.id desc
                       ) as row_number
                from product_like_histories h
                where h.user_id = :userId
                  and h.product_id in (:productIds)
            ) latest
            where latest.row_number = 1
              and latest.action = 'REGISTER'
        """,
        nativeQuery = true,
    )
    fun findLikedProductIds(
        @Param("userId") userId: Long,
        @Param("productIds") productIds: Collection<Long>,
    ): Set<Long>

    fun countByUserIdAndProductId(userId: Long, productId: Long): Long
}
