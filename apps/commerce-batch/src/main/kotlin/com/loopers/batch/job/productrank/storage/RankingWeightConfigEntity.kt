package com.loopers.batch.job.productrank.storage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

/**
 * commerce-api 소유 테이블의 읽기 전용 미러 매핑 (status는 문자열로 단순화 — 배치는 'ACTIVE'만 필터).
 * 로컬/테스트 스키마 생성용. 스키마 변경 시 api의 RankingWeightConfigEntity와 동기화할 것.
 */
@Entity
@Table(name = "ranking_weight_config")
class RankingWeightConfigEntity(
    @Id
    @Column(name = "version", length = 10)
    val version: String,

    @Column(name = "view_weight", nullable = false)
    var viewWeight: Long,

    @Column(name = "like_weight", nullable = false)
    var likeWeight: Long,

    @Column(name = "order_weight", nullable = false)
    var orderWeight: Long,

    @Column(name = "status", nullable = false, length = 20)
    var status: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime,

    @Column(name = "activated_at")
    var activatedAt: ZonedDateTime? = null,
)
