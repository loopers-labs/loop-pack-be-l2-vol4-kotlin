package com.loopers.product.application

import com.loopers.brand.domain.Brand
import com.loopers.brand.domain.BrandErrorCode
import com.loopers.brand.domain.BrandName
import com.loopers.brand.domain.BrandRepository
import com.loopers.inventory.domain.Inventory
import com.loopers.inventory.domain.InventoryRepository
import com.loopers.product.domain.PriceCursor
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductErrorCode
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.product.domain.ProductSort
import com.loopers.product.domain.ProductStatus
import com.loopers.shared.domain.CursorPage
import com.loopers.shared.domain.Money
import com.loopers.support.error.ConflictException
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
import org.springframework.context.ApplicationEventPublisher

class ProductServiceTest {
    private val productRepository: ProductRepository = mock()
    private val brandRepository: BrandRepository = mock()
    private val inventoryRepository: InventoryRepository = mock()
    private val eventPublisher: ApplicationEventPublisher = mock()
    private val productService = ProductService(productRepository, brandRepository, inventoryRepository, eventPublisher)

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

    @DisplayName("요청한 ID가 전부 활성 상품이고 가격도 일치하면, ID를 키로 한 맵을 반환한다.")
    @Test
    fun returnsActiveProductsMap() {
        val product = product()
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id))).thenReturn(listOf(product))

        val result = productService.getActiveProducts(listOf(ProductCheckCommand(product.id, 100_000)))

        assertThat(result).containsEntry(product.id, product)
    }

    @DisplayName("요청한 ID 중 하나라도 활성 상품이 아니면, NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenAnyProductMissing() {
        val product = product()
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id, 99L))).thenReturn(listOf(product))

        val result = assertThrows<NotFoundException> {
            productService.getActiveProducts(
                listOf(ProductCheckCommand(product.id, 100_000), ProductCheckCommand(99L, 100_000)),
            )
        }
        assertThat(result.errorCode).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND)
    }

    @DisplayName("요청한 가격이 상품의 등록 가격과 다르면, CONFLICT 예외(PRODUCT_PRICE_NOT_MATCHED)가 발생한다.")
    @Test
    fun throwsConflict_whenRequestedPriceDoesNotMatch() {
        val product = product()
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id))).thenReturn(listOf(product))

        val result = assertThrows<ConflictException> {
            productService.getActiveProducts(listOf(ProductCheckCommand(product.id, 99_999)))
        }
        assertThat(result.errorCode).isEqualTo(ProductErrorCode.PRODUCT_PRICE_NOT_MATCHED)
    }
}
