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
            set like_count = like_count + :likeDelta,
                sales_count = sales_count + :salesDelta,
                view_count = view_count + :viewDelta,
                last_event_at = case
                    when last_event_at is null or last_event_at <= :occurredAt then :occurredAt
                    else last_event_at
                end,
                last_like_event_at = case
                    when :likeDelta <> 0
                        and (last_like_event_at is null or last_like_event_at <= :occurredAt) then :occurredAt
                    else last_like_event_at
                end,
                last_sales_event_at = case
                    when :salesDelta <> 0
                        and (last_sales_event_at is null or last_sales_event_at <= :occurredAt) then :occurredAt
                    else last_sales_event_at
                end,
                last_view_event_at = case
                    when :viewDelta <> 0
                        and (last_view_event_at is null or last_view_event_at <= :occurredAt) then :occurredAt
                    else last_view_event_at
                end,
                updated_at = current_timestamp
            where product_id = :productId
              and like_count + :likeDelta >= 0
              and sales_count + :salesDelta >= 0
              and view_count + :viewDelta >= 0
        """,
        nativeQuery = true,
    )
    fun applyDelta(
        @Param("productId") productId: Long,
        @Param("likeDelta") likeDelta: Int,
        @Param("salesDelta") salesDelta: Long,
        @Param("viewDelta") viewDelta: Int,
        @Param("occurredAt") occurredAt: ZonedDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            insert ignore into product_metrics (
                product_id,
                like_count,
                sales_count,
                view_count,
                last_event_at,
                last_like_event_at,
                last_sales_event_at,
                last_view_event_at,
                updated_at
            )
            select :productId,
                   :likeDelta,
                   :salesDelta,
                   :viewDelta,
                   :occurredAt,
                   case when :likeDelta <> 0 then :occurredAt else null end,
                   case when :salesDelta <> 0 then :occurredAt else null end,
                   case when :viewDelta <> 0 then :occurredAt else null end,
                   current_timestamp
            where :likeDelta >= 0
              and :salesDelta >= 0
              and :viewDelta >= 0
        """,
        nativeQuery = true,
    )
    fun insertDeltaIfAbsent(
        @Param("productId") productId: Long,
        @Param("likeDelta") likeDelta: Int,
        @Param("salesDelta") salesDelta: Long,
        @Param("viewDelta") viewDelta: Int,
        @Param("occurredAt") occurredAt: ZonedDateTime,
    ): Int
}
