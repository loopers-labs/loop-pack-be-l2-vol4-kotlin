package com.loopers.failure.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface ConsumedEventFailureJpaRepository : JpaRepository<ConsumedEventFailure, Long>
