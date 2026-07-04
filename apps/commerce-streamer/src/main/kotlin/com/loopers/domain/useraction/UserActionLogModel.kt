package com.loopers.domain.useraction

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "user_action_log")
class UserActionLogModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id")
    val userId: Long,
    @Column(name = "action_type")
    val actionType: String,
    @Column(name = "target_id")
    val targetId: Long,
    @Column(name = "occurred_at")
    val occurredAt: ZonedDateTime,
)
