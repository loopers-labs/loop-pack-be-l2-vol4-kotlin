package com.loopers.application.catalog

import com.loopers.application.catalog.port.CatalogProductStatsCommandPort
import org.springframework.stereotype.Component

@Component
class CatalogProductStatsCommandAdapter(
    private val catalogApplicationService: CatalogApplicationService,
) : CatalogProductStatsCommandPort {
    override fun increaseLikeCount(productId: Long) {
        catalogApplicationService.increaseLikeCount(productId)
    }

    override fun decreaseLikeCount(productId: Long) {
        catalogApplicationService.decreaseLikeCount(productId)
    }
}
