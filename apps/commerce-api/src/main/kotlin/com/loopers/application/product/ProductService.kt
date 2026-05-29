package com.loopers.application.product

import com.loopers.domain.brand.BrandErrorCode
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorCode
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.shared.Cursor
import com.loopers.domain.shared.CursorPage
import com.loopers.domain.shared.Money
import com.loopers.support.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
) {
    @Transactional
    fun register(command: ProductCreateCommand): ProductInfo {
        brandRepository.findActiveById(command.brandId)
            ?: throw NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)
        val product = productRepository.save(
            Product(
                brandId = command.brandId,
                name = ProductName(command.name),
                price = Money(command.price),
            ),
        )
        return ProductInfo.from(product)
    }

    @Transactional
    fun update(command: ProductUpdateCommand): ProductInfo {
        val product = productRepository.findActiveById(command.id)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        product.update(ProductName(command.name), Money(command.price))
        return ProductInfo.from(product)
    }

    @Transactional
    fun delete(id: Long) {
        val product = productRepository.findActiveById(id)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        product.transitionTo(ProductStatus.DELETED)
    }

    /**
     * 브랜드 삭제 cascade: 해당 브랜드의 활성 상품을 일괄 soft delete 한다.
     * 호출 주체는 [com.loopers.application.brand.BrandFacade] (브랜드 삭제 use case와 조합).
     */
    @Transactional
    fun softDeleteByBrand(brandId: Long) {
        productRepository.findActiveByBrandId(brandId)
            .forEach { it.transitionTo(ProductStatus.DELETED) }
    }

    @Transactional(readOnly = true)
    fun list(sort: ProductSort, brandId: Long?, cursor: Cursor?, size: Int): CursorPage<ProductInfo> {
        val page = productRepository.findAll(sort, brandId, cursor, size)
        return CursorPage(page.content.map(ProductInfo::from), page.hasNext, page.nextCursor)
    }

    @Transactional(readOnly = true)
    fun getDetail(id: Long): ProductDetailInfo {
        val product = productRepository.findActiveById(id)
            ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
        val brand = brandRepository.findActiveById(product.brandId)
            ?: throw NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)
        return ProductDetailInfo.of(product, brand.name.value)
    }
}

data class ProductCreateCommand(
    val brandId: Long,
    val name: String,
    val price: Long,
)

data class ProductUpdateCommand(
    val id: Long,
    val name: String,
    val price: Long,
)

data class ProductInfo(
    val id: Long,
    val brandId: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
) {
    companion object {
        fun from(product: Product): ProductInfo =
            ProductInfo(
                id = product.id,
                brandId = product.brandId,
                name = product.name.value,
                price = product.price.amount,
                likeCount = product.likeCount,
            )
    }
}

data class ProductDetailInfo(
    val id: Long,
    val brandId: Long,
    val brandName: String,
    val name: String,
    val price: Long,
    val likeCount: Long,
) {
    companion object {
        fun of(product: Product, brandName: String): ProductDetailInfo =
            ProductDetailInfo(
                id = product.id,
                brandId = product.brandId,
                brandName = brandName,
                name = product.name.value,
                price = product.price.amount,
                likeCount = product.likeCount,
            )
    }
}
