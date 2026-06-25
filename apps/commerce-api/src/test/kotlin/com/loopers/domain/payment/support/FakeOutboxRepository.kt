package com.loopers.domain.payment.support

import com.loopers.domain.payment.model.OutboxEventModel
import com.loopers.domain.payment.port.OutboxRepository

class FakeOutboxRepository : OutboxRepository {
    val events: MutableList<OutboxEventModel> = mutableListOf()

    override fun save(event: OutboxEventModel): OutboxEventModel {
        val saved = event.copy(id = events.size + 1L)
        events.add(saved)
        return saved
    }
}
