package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetricsModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductMetricsJpaRepository : JpaRepository<ProductMetricsModel, Long> {
    /**
     * 원자적 upsert. 행이 없으면 INSERT, 있으면 각 카운터를 delta 만큼 증감한다.
     * MySQL row-level lock 으로 동시 갱신 시에도 손실 없이 누적된다.
     * 각 카운터는 GREATEST(0, ...) 로 0 하한을 둬, 순서 역전/재처리 등으로도 음수 지표가 저장되지 않는다.
     * (INSERT VALUES 경로도 함께 하한을 적용해야 "좋아요 없이 unlike 선행" 케이스에서 -1 이 새 행으로 들어가지 않는다.)
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, updated_at)
            VALUES (:productId, GREATEST(0, :likeDelta), GREATEST(0, :viewDelta), GREATEST(0, :salesDelta), NOW())
            ON DUPLICATE KEY UPDATE
                like_count = GREATEST(0, like_count + :likeDelta),
                view_count = GREATEST(0, view_count + :viewDelta),
                sales_count = GREATEST(0, sales_count + :salesDelta),
                updated_at = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("productId") productId: Long,
        @Param("likeDelta") likeDelta: Long,
        @Param("viewDelta") viewDelta: Long,
        @Param("salesDelta") salesDelta: Long,
    ): Int
}
