package com.loopers.metrics.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface EventHandledJpaRepository : JpaRepository<EventHandled, EventHandledId>
