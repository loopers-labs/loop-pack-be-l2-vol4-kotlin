package com.loopers.batch.job.productrank.storage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 기간 집계의 중간 결과 (상품별 점수 합). RankConfirm Step이 TOP 100을 뽑아 MV로 옮긴 뒤 비운다.
 * Step 시작 시 항상 TRUNCATE되므로 Job 간 상태를 공유하지 않는다 — PK가 product_id 하나인 이유.
 */
@Entity
@Table(name = "product_rank_staging")
class ProductRankStagingEntity(
    @Id
    @Column(name = "product_id")
    val productId: Long = 0L,

    @Column(name = "score", nullable = false)
    var score: Long = 0L,
)
