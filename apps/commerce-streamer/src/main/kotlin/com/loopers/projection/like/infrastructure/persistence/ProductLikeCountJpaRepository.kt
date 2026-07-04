package com.loopers.projection.like.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface ProductLikeCountJpaRepository : JpaRepository<ProductLikeCountJpaEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            update product_metrics
            set like_count = like_count + 1,
                updated_at = current_timestamp
            where product_id = :productId
        """,
        nativeQuery = true,
    )
    fun increment(
        @Param("productId") productId: Long,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            update product_metrics
            set like_count = like_count - 1,
                updated_at = current_timestamp
            where product_id = :productId and like_count > 0
        """,
        nativeQuery = true,
    )
    fun decrement(
        @Param("productId") productId: Long,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            update product_metrics
            set like_count = like_count + :likeDelta,
                sales_count = sales_count + :salesDelta,
                view_count = view_count + :viewDelta,
                last_event_at = case
                    when last_event_at is null or last_event_at <= :occurredAt then :occurredAt
                    else last_event_at
                end,
                last_like_event_at = case
                    when :likeDelta <> 0 then :occurredAt
                    else last_like_event_at
                end,
                last_sales_event_at = case
                    when :salesDelta <> 0 then :occurredAt
                    else last_sales_event_at
                end,
                last_view_event_at = case
                    when :viewDelta <> 0 then :occurredAt
                    else last_view_event_at
                end,
                updated_at = current_timestamp
            where product_id = :productId
              and like_count + :likeDelta >= 0
              and sales_count + :salesDelta >= 0
              and view_count + :viewDelta >= 0
              and (:likeDelta = 0 or last_like_event_at is null or last_like_event_at <= :occurredAt)
              and (:salesDelta = 0 or last_sales_event_at is null or last_sales_event_at <= :occurredAt)
              and (:viewDelta = 0 or last_view_event_at is null or last_view_event_at <= :occurredAt)
        """,
        nativeQuery = true,
    )
    fun applyDelta(
        @Param("productId") productId: Long,
        @Param("likeDelta") likeDelta: Int,
        @Param("salesDelta") salesDelta: Int,
        @Param("viewDelta") viewDelta: Int,
        @Param("occurredAt") occurredAt: ZonedDateTime,
    ): Int

    @Query(
        value = """
            select count(*)
            from product_metrics
            where product_id = :productId
              and (:likeDelta = 0 or last_like_event_at is null or last_like_event_at <= :occurredAt)
              and (:salesDelta = 0 or last_sales_event_at is null or last_sales_event_at <= :occurredAt)
              and (:viewDelta = 0 or last_view_event_at is null or last_view_event_at <= :occurredAt)
        """,
        nativeQuery = true,
    )
    fun countFreshDeltaCandidates(
        @Param("productId") productId: Long,
        @Param("likeDelta") likeDelta: Int,
        @Param("salesDelta") salesDelta: Int,
        @Param("viewDelta") viewDelta: Int,
        @Param("occurredAt") occurredAt: ZonedDateTime,
    ): Long
}
