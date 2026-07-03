package com.loopers.domain.event

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "events")
class Event(
    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @Column(name = "starts_at", nullable = false)
    val startsAt: LocalDateTime,

    @Column(name = "ends_at", nullable = false)
    val endsAt: LocalDateTime,
) : BaseEntity() {
    init {
        validate()
    }

    override fun guard() {
        validate()
    }

    fun isActive(now: LocalDateTime): Boolean =
        !now.isBefore(startsAt) && now.isBefore(endsAt)

    private fun validate() {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "이벤트명은 비어있을 수 없습니다.")
        if (!startsAt.isBefore(endsAt)) throw CoreException(ErrorType.BAD_REQUEST, "이벤트 종료 시각은 시작 시각 이후여야 합니다.")
    }
}
