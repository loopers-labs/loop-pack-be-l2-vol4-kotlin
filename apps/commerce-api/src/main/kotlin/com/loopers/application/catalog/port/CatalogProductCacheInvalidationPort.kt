package com.loopers.application.catalog.port

interface CatalogProductCacheInvalidationPort {
    fun evictProduct(productId: Long)

    fun evictBrandProducts(brandId: Long)

    object NoOp : CatalogProductCacheInvalidationPort {
        override fun evictProduct(productId: Long) = Unit

        override fun evictBrandProducts(brandId: Long) = Unit
    }
}
