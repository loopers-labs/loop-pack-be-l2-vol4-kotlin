package com.loopers.application.product

import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.product.LikeCountQueryPort
import com.loopers.domain.product.ProductDetailComposer
import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.domain.product.ProductService
import com.loopers.domain.stock.StockRepositoryPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProductConfig {
    @Bean
    fun productService(
        productRepositoryPort: ProductRepositoryPort,
        stockRepositoryPort: StockRepositoryPort,
    ): ProductService = ProductService(productRepositoryPort, stockRepositoryPort)

    @Bean
    fun productDetailComposer(
        brandRepositoryPort: BrandRepositoryPort,
        stockRepositoryPort: StockRepositoryPort,
        likeCountQueryPort: LikeCountQueryPort,
    ): ProductDetailComposer = ProductDetailComposer(brandRepositoryPort, stockRepositoryPort, likeCountQueryPort)
}
