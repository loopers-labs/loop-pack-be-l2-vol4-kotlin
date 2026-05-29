package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeEvent
import org.springframework.data.jpa.repository.JpaRepository

interface LikeEventJpaRepository : JpaRepository<LikeEvent, Long>
