package com.loopers.application.catalog

import com.loopers.application.order.CatalogStockPort
import com.loopers.domain.catalog.ProductStockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class CatalogOrderStockAdapter(
    private val productStockRepository: ProductStockRepository,
) : CatalogStockPort {
    override fun lockStocks(productIds: Collection<Long>): List<CatalogStockPort.StockRow> {
        return productStockRepository.lockAllByProductIds(productIds)
            .map { CatalogStockPort.StockRow(productId = it.productId, stockQuantity = it.stockQuantity) }
    }

    override fun deductAll(quantitiesByProductId: Map<Long, Int>) {
        val stocks = productStockRepository.lockAllByProductIds(quantitiesByProductId.keys)
            .associateBy { it.productId }
        if (stocks.keys != quantitiesByProductId.keys) {
            throw CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")
        }
        quantitiesByProductId.toSortedMap().forEach { (productId, quantity) ->
            stocks.getValue(productId).deduct(quantity)
        }
    }

    override fun restoreAll(quantitiesByProductId: Map<Long, Int>) {
        val stocks = productStockRepository.lockAllByProductIds(quantitiesByProductId.keys)
            .associateBy { it.productId }
        if (stocks.keys != quantitiesByProductId.keys) {
            throw CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")
        }
        quantitiesByProductId.toSortedMap().forEach { (productId, quantity) ->
            stocks.getValue(productId).restore(quantity)
        }
    }
}
