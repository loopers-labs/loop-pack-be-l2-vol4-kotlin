package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandErrorCode
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.inventory.Inventory
import com.loopers.domain.inventory.InventoryRepository
import com.loopers.domain.product.PriceCursor
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorCode
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.shared.CursorPage
import com.loopers.domain.shared.Money
import com.loopers.support.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProductServiceTest {
    private val productRepository: ProductRepository = mock()
    private val brandRepository: BrandRepository = mock()
    private val inventoryRepository: InventoryRepository = mock()
    private val productService = ProductService(productRepository, brandRepository, inventoryRepository)

    private fun product() = Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(100_000))

    @DisplayName("브랜드가 존재하면, 상품을 저장하고 초기 재고를 함께 생성한 뒤 정보를 반환한다.")
    @Test
    fun savesProductAndInventory_whenBrandExists() {
        whenever(brandRepository.findActiveById(1L)).thenReturn(Brand(BrandName("나이키")))
        whenever(productRepository.save(any())).thenAnswer { it.arguments[0] as Product }

        val info = productService.register(
            ProductCreateCommand(brandId = 1L, name = "에어맥스", price = 100_000, stock = 50),
        )

        val productCaptor = argumentCaptor<Product>()
        val inventoryCaptor = argumentCaptor<Inventory>()
        verify(productRepository).save(productCaptor.capture())
        verify(inventoryRepository).save(inventoryCaptor.capture())
        assertAll(
            { assertThat(productCaptor.firstValue.name.value).isEqualTo("에어맥스") },
            { assertThat(productCaptor.firstValue.price).isEqualTo(Money(100_000)) },
            { assertThat(productCaptor.firstValue.brandId).isEqualTo(1L) },
            { assertThat(inventoryCaptor.firstValue.quantity).isEqualTo(50) },
            { assertThat(info.name).isEqualTo("에어맥스") },
            { assertThat(info.price).isEqualTo(100_000) },
            { assertThat(info.brandId).isEqualTo(1L) },
        )
    }

    @DisplayName("브랜드가 존재하지 않으면, NOT_FOUND 예외가 발생하고 상품·재고 모두 저장하지 않는다.")
    @Test
    fun throwsNotFound_whenBrandDoesNotExist() {
        whenever(brandRepository.findActiveById(99L)).thenReturn(null)

        val result = assertThrows<NotFoundException> {
            productService.register(ProductCreateCommand(brandId = 99L, name = "에어맥스", price = 100_000, stock = 50))
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(BrandErrorCode.BRAND_NOT_FOUND) },
            { verify(productRepository, Mockito.never()).save(any()) },
            { verify(inventoryRepository, Mockito.never()).save(any()) },
        )
    }

    @DisplayName("상품을 수정하면, 이름과 가격이 변경되고 브랜드는 불변이다.")
    @Test
    fun updatesNameAndPrice() {
        val product = product()
        whenever(productRepository.findActiveById(10L)).thenReturn(product)

        val info = productService.update(ProductUpdateCommand(id = 10L, name = "줌플라이", price = 150_000))

        assertAll(
            { assertThat(product.name.value).isEqualTo("줌플라이") },
            { assertThat(product.price).isEqualTo(Money(150_000)) },
            { assertThat(product.brandId).isEqualTo(1L) },
            { assertThat(info.name).isEqualTo("줌플라이") },
            { assertThat(info.price).isEqualTo(150_000) },
        )
    }

    @DisplayName("존재하지 않는 상품을 수정하면, NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenUpdateTargetMissing() {
        whenever(productRepository.findActiveById(99L)).thenReturn(null)

        val result = assertThrows<NotFoundException> {
            productService.update(ProductUpdateCommand(id = 99L, name = "줌플라이", price = 150_000))
        }
        assertThat(result.errorCode).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND)
    }

    @DisplayName("상품을 삭제하면, DELETED 상태로 전이되고 해당 상품의 재고가 archive 된다.")
    @Test
    fun softDeletesProductAndArchivesInventory() {
        val product = product()
        val inventory = Inventory.createFor(product.id, 50)
        whenever(productRepository.findActiveById(10L)).thenReturn(product)
        whenever(inventoryRepository.findAllByProductIdIn(listOf(product.id))).thenReturn(listOf(inventory))

        productService.delete(10L)

        assertAll(
            { assertThat(product.status).isEqualTo(ProductStatus.DELETED) },
            { assertThat(inventory.deletedAt).isNotNull() },
        )
        verify(inventoryRepository).save(inventory)
    }

    @DisplayName("브랜드 cascade 삭제는, 해당 브랜드의 활성 상품을 모두 DELETED로 전이하고 재고를 archive 한다.")
    @Test
    fun softDeleteByBrand_transitionsAllActiveProductsAndArchivesInventory() {
        val first = product()
        val second = product()
        val inventory = Inventory.createFor(first.id, 50)
        whenever(productRepository.findActiveByBrandId(1L)).thenReturn(listOf(first, second))
        whenever(inventoryRepository.findAllByProductIdIn(listOf(first.id, second.id))).thenReturn(listOf(inventory))

        productService.softDeleteByBrand(1L)

        assertAll(
            { assertThat(first.status).isEqualTo(ProductStatus.DELETED) },
            { assertThat(second.status).isEqualTo(ProductStatus.DELETED) },
            { assertThat(inventory.deletedAt).isNotNull() },
        )
        verify(inventoryRepository).save(inventory)
    }

    @DisplayName("목록 조회는 정렬·브랜드 필터·커서·크기를 리포지토리에 위임하고 nextCursor를 전달한다.")
    @Test
    fun listDelegatesToRepository() {
        val cursor = PriceCursor(price = 100_000, id = 20L)
        val nextCursor = PriceCursor(price = 150_000, id = 10L)
        whenever(productRepository.findAll(eq(ProductSort.PRICE_ASC), eq(1L), eq(cursor), eq(5)))
            .thenReturn(CursorPage(listOf(product()), hasNext = true, nextCursor = nextCursor))

        val page = productService.list(ProductSort.PRICE_ASC, brandId = 1L, cursor = cursor, size = 5)

        assertAll(
            { assertThat(page.hasNext).isTrue() },
            { assertThat(page.content.map { it.name }).containsExactly("에어맥스") },
            { assertThat(page.nextCursor).isEqualTo(nextCursor) },
        )
        verify(productRepository).findAll(ProductSort.PRICE_ASC, 1L, cursor, 5)
    }

    @DisplayName("상품 상세는 상품·브랜드명·좋아요 수를 조합해 반환한다.")
    @Test
    fun detailCombinesProductBrandAndLikeCount() {
        val product = product()
        whenever(productRepository.findActiveById(10L)).thenReturn(product)
        whenever(brandRepository.findActiveById(1L)).thenReturn(Brand(BrandName("나이키")))

        val detail = productService.getDetail(10L)

        assertAll(
            { assertThat(detail.name).isEqualTo("에어맥스") },
            { assertThat(detail.brandId).isEqualTo(1L) },
            { assertThat(detail.brandName).isEqualTo("나이키") },
            { assertThat(detail.likeCount).isEqualTo(0L) },
            { assertThat(detail.price).isEqualTo(100_000) },
        )
    }

    @DisplayName("존재하지 않는 상품의 상세를 조회하면, NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenDetailTargetMissing() {
        whenever(productRepository.findActiveById(99L)).thenReturn(null)

        val result = assertThrows<NotFoundException> {
            productService.getDetail(99L)
        }
        assertThat(result.errorCode).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND)
    }
}
