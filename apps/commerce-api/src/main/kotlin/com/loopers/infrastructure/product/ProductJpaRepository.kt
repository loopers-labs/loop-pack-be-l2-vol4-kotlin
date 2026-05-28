package com.loopers.infrastructure.product

import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository :
    JpaRepository<ProductEntity, Long>,
    ProductQueryRepository
