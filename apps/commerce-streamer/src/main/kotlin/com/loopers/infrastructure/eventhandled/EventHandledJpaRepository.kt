package com.loopers.infrastructure.eventhandled

import org.springframework.data.jpa.repository.JpaRepository

interface EventHandledJpaRepository : JpaRepository<EventHandledJpaEntity, String>
