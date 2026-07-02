package com.loopers.domain.event

import com.loopers.domain.event.model.EventOutbox

interface ExternalEventPublisher {
    fun publish(eventOutbox: EventOutbox)
}
