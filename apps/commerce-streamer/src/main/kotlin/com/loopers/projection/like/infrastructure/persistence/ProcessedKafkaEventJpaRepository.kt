package com.loopers.projection.like.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedKafkaEventJpaRepository : JpaRepository<ProcessedKafkaEventJpaEntity, ProcessedKafkaEventJpaId>
