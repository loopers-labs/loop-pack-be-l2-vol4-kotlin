package com.loopers.useractivity.domain

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

// 유저 행동 이력(append-only) — 분석·감사용 원본. 컨슈머 멱등용 event_handled 와는 목적이 달라 별개 테이블.
// 발생 시각 = BaseEntity.createdAt, 유실 허용(outbox 미경유).
@Entity
@Table(name = "user_action_log")
class UserActionLog(
    userId: Long?,
    actionType: UserActionType,
    targetType: String,
    targetId: Long,
) : BaseEntity() {
    // 비로그인 조회 등 행위자를 모르는 행동은 null
    @Column(name = "user_id", updatable = false)
    val userId: Long? = userId

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 10, updatable = false)
    val actionType: UserActionType = actionType

    @Column(name = "target_type", nullable = false, length = 30, updatable = false)
    val targetType: String = targetType

    @Column(name = "target_id", nullable = false, updatable = false)
    val targetId: Long = targetId
}

enum class UserActionType { VIEW, LIKE, UNLIKE, ORDER }
