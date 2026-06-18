package com.loopers.domain.coupon.infrastructure.persistence.template

import org.springframework.data.jpa.repository.JpaRepository

interface CouponTemplateJpaRepository : JpaRepository<CouponTemplateJpaEntity, Long>
