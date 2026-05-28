package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductDetail

interface ProductApplicationServicePort {
    fun getProduct(id: Long): ProductDetail
}
