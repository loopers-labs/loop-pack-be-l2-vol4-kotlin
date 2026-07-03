package com.loopers.metrics.infrastructure

import com.loopers.metrics.domain.EventHandled
import org.springframework.data.jpa.repository.JpaRepository

interface EventHandledJpaRepository : JpaRepository<EventHandled, String>
