package com.loopers.infrastructure.ranking

import org.springframework.data.jpa.repository.JpaRepository

interface RankingEventHandledJpaRepository : JpaRepository<RankingEventHandledJpaEntity, String>
