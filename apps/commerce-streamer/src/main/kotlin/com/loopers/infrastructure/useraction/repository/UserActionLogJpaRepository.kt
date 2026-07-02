package com.loopers.infrastructure.useraction.repository

import com.loopers.infrastructure.useraction.entity.UserActionLogEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserActionLogJpaRepository : JpaRepository<UserActionLogEntity, Long>
