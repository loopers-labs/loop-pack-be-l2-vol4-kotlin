package com.loopers.domain.coupon.infrastructure.persistence.event

import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueEventHandledJpaRepository :
    JpaRepository<CouponIssueEventHandledJpaEntity, CouponIssueEventHandledJpaId>
