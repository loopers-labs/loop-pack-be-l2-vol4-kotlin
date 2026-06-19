package com.loopers.application.order

interface CatalogStockPort {
    fun reserveAll(quantitiesByProductId: Map<Long, Int>)

    fun confirmReservedAll(quantitiesByProductId: Map<Long, Int>)

    fun releaseReservedAll(quantitiesByProductId: Map<Long, Int>)

    fun restoreActualAll(quantitiesByProductId: Map<Long, Int>)
}
