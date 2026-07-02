package com.loopers.useractivity.infrastructure

import com.loopers.useractivity.domain.UserActionLog
import org.springframework.data.jpa.repository.JpaRepository

interface UserActionLogJpaRepository : JpaRepository<UserActionLog, Long>
