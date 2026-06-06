package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository

interface CouponTemplateJpaRepository : JpaRepository<CouponTemplateEntity, Long>
