package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingBaseline
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRankingBaselineRepository : JpaRepository<ProductRankingBaseline, Long>
