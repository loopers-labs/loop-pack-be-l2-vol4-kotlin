package com.loopers.infrastructure.idempotency

import com.loopers.domain.idempotency.EventHandledModel
import com.loopers.domain.idempotency.EventHandledRepository
import org.springframework.stereotype.Repository

/**
 * EventHandledRepository JPA 구현체.
 */
@Repository
class EventHandledRepositoryImpl(
    private val jpaRepository: EventHandledJpaRepository,
) : EventHandledRepository {

    /** eventId로 이미 처리된 이벤트인지 확인한다. */
    override fun existsByEventId(eventId: String): Boolean {
        return jpaRepository.existsByEventId(eventId)
    }

    /** 처리 완료된 이벤트를 저장한다. */
    override fun save(model: EventHandledModel): EventHandledModel {
        return jpaRepository.save(model)
    }
}
