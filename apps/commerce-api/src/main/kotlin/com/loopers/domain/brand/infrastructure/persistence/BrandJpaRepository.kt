package com.loopers.domain.brand.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface BrandJpaRepository : JpaRepository<BrandJpaEntity, Long>
