package com.loopers.like.infrastructure

import com.loopers.like.domain.LikeEvent
import org.springframework.data.jpa.repository.JpaRepository

interface LikeEventJpaRepository : JpaRepository<LikeEvent, Long>
