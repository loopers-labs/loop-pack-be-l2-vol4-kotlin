package com.loopers.application.product

import com.loopers.domain.brand.BrandService
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.LikeService
import com.loopers.domain.order.OrderService
import com.loopers.domain.product.ProductDetail
import com.loopers.domain.product.ProductDetailComposer
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductSummary
import com.loopers.interfaces.api.product.ProductAdminApplicationServicePort
import com.loopers.interfaces.api.product.ProductApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductApplicationServiceAdapter(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val likeService: LikeService,
    private val productDetailComposer: ProductDetailComposer,
    private val orderService: OrderService,
) : ProductApplicationServicePort,
    ProductAdminApplicationServicePort {
    @Transactional(readOnly = true)
    override fun getProduct(id: Long): ProductDetail {
        val product = productService.getById(id)
        return productDetailComposer.compose(product)
    }

    @Transactional(readOnly = true)
    override fun getProducts(brandId: Long?, sort: ProductSort, pageRequest: PageRequest): PageResult<ProductSummary> {
        val products = productService.getAll(brandId, sort, pageRequest)
        val summaries = productDetailComposer.composeAll(products.items)
        return PageResult(
            items = summaries,
            page = products.page,
            size = products.size,
            totalElements = products.totalElements,
            totalPages = products.totalPages,
        )
    }

    @Transactional
    override fun createProduct(command: CreateProductCommand): ProductDetail {
        val brand = brandService.getById(command.brandId)
        val (product, stock) = productService.create(
            name = command.name,
            price = command.price,
            description = command.description,
            brandId = command.brandId,
            quantity = command.quantity,
        )
        likeService.initializeForProduct(product.id)
        return ProductDetail.of(product, brand, stock, likeCount = 0L)
    }

    @Transactional
    override fun updateProduct(command: UpdateProductCommand): ProductDetail {
        val (updatedProduct, _) = productService.update(
            id = command.id,
            name = command.name,
            price = command.price,
            description = command.description,
            brandId = command.brandId,
            quantity = command.quantity,
        )
        return productDetailComposer.compose(updatedProduct)
    }

    @Transactional
    override fun deleteProduct(id: Long) {
        if (orderService.hasIncompleteOrdersForProducts(listOf(id))) {
            throw CoreException(ErrorType.CONFLICT, "미완료 주문이 있는 상품은 삭제할 수 없습니다.")
        }
        productService.delete(id)
        likeService.deleteAllByProductId(id)
    }
}
