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
    override fun reserveAll(quantitiesByProductId: Map<Long, Int>) {
        quantitiesByProductId.toSortedMap().forEach { (productId, quantity) ->
            if (!productStockRepository.reserveIfAvailable(productId, quantity)) {
                throw CoreException(ErrorType.CONFLICT, "재고가 부족합니다.")
            }
        }
    }

    override fun confirmReservedAll(quantitiesByProductId: Map<Long, Int>) {
        quantitiesByProductId.toSortedMap().forEach { (productId, quantity) ->
            if (!productStockRepository.confirmReserved(productId, quantity)) {
                throw CoreException(ErrorType.CONFLICT, "예약 재고 확정에 실패했습니다.")
            }
        }
    }

    override fun releaseReservedAll(quantitiesByProductId: Map<Long, Int>) {
        quantitiesByProductId.toSortedMap().forEach { (productId, quantity) ->
            if (!productStockRepository.releaseReserved(productId, quantity)) {
                throw CoreException(ErrorType.CONFLICT, "예약 재고 반환에 실패했습니다.")
            }
        }
    }

    override fun restoreActualAll(quantitiesByProductId: Map<Long, Int>) {
        quantitiesByProductId.toSortedMap().forEach { (productId, quantity) ->
            if (!productStockRepository.restoreActualStock(productId, quantity)) {
                throw CoreException(ErrorType.CONFLICT, "실재고 복구에 실패했습니다.")
            }
        }
    }
}
