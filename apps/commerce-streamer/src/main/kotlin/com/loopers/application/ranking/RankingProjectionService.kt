package com.loopers.application.ranking

import com.loopers.event.CatalogEventMessage
import com.loopers.event.OrderEventMessage
import org.springframework.stereotype.Component

@Component
class RankingProjectionService {
    fun projectCatalog(message: CatalogEventMessage) = Unit

    fun projectOrder(message: OrderEventMessage) = Unit
}
