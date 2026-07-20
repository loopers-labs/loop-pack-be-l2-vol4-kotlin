package com.loopers.infrastructure.coupon.repository

import com.loopers.infrastructure.coupon.entity.CouponIssueRequestEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponIssueRequestJpaRepository : JpaRepository<CouponIssueRequestEntity, Long> {
    fun findByRequestId(requestId: String): CouponIssueRequestEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from CouponIssueRequestEntity request where request.requestId = :requestId")
    fun findByRequestIdForUpdate(@Param("requestId") requestId: String): CouponIssueRequestEntity?
}
