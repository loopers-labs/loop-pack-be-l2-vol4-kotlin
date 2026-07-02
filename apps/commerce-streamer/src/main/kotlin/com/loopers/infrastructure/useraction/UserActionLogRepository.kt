package com.loopers.infrastructure.useraction

import com.loopers.domain.useraction.UserActionLogModel
import org.springframework.data.jpa.repository.JpaRepository

interface UserActionLogRepository : JpaRepository<UserActionLogModel, Long>
