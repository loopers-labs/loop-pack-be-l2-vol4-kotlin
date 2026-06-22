package com.loopers.infrastructure.coupon.repository

import com.loopers.infrastructure.coupon.entity.CouponIssueEntity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponIssueJpaRepository : JpaRepository<CouponIssueEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select couponIssue from CouponIssueEntity couponIssue where couponIssue.id = :issueId")
    fun findByIdForUpdate(@Param("issueId") issueId: Long): CouponIssueEntity?

    fun findAllByMemberIdOrderByCreatedAtDescIdDesc(memberId: Long): List<CouponIssueEntity>

    fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<CouponIssueEntity>

    fun existsByCouponId(couponId: Long): Boolean
}
