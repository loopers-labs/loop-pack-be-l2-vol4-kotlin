package com.loopers.domain.product.unit

import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.product.application.command.ProductSearchCommand
import com.loopers.domain.product.model.ProductModel
import com.loopers.domain.product.port.ProductRepository
import com.loopers.domain.product.support.ProductSteps.Companion.기본_상품_ID
import com.loopers.domain.product.support.ProductSteps.Companion.상품_도메인_생성
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.product.support.ProductSteps.Companion.상품_수정_커맨드
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class ProductServiceTest {
    @Test
    fun `상품을_등록한다`() {
        val productRepository = mockk<ProductRepository>()
        val productService = ProductService(productRepository)
        val productSlot = slot<ProductModel>()
        every { productRepository.save(capture(productSlot)) } answers {
            productSlot.captured.copy(id = 기본_상품_ID)
        }

        val product = productService.register(상품_등록_커맨드())

        assertThat(product.id).isEqualTo(기본_상품_ID)
        assertThat(productSlot.captured.brandId).isEqualTo(10L)
        assertThat(productSlot.captured.name.value).isEqualTo("기본 상품")
        assertThat(productSlot.captured.price.value).isEqualTo(10_000)
    }

    @Test
    fun `상품을_수정한다`() {
        val productRepository = mockk<ProductRepository>()
        val productService = ProductService(productRepository)
        every { productRepository.findById(기본_상품_ID) } returns 상품_도메인_생성(id = 기본_상품_ID)
        every { productRepository.save(any()) } answers { firstArg() }

        val product = productService.update(
            productId = 기본_상품_ID,
            command = 상품_수정_커맨드(name = "변경 상품", price = 20_000),
        )

        assertThat(product.name.value).isEqualTo("변경 상품")
        assertThat(product.price.value).isEqualTo(20_000)
    }

    @Test
    fun `존재하지_않는_상품_조회는_NOT_FOUND가_발생한다`() {
        val productRepository = mockk<ProductRepository>()
        val productService = ProductService(productRepository)
        every { productRepository.findById(기본_상품_ID) } returns null

        val ex = assertThrows<CoreException> {
            productService.findById(기본_상품_ID)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
    }

    @Test
    fun `삭제된_상품은_주문_스냅샷_조회가_불가하다`() {
        val productRepository = mockk<ProductRepository>()
        val productService = ProductService(productRepository)
        every { productRepository.findAllByIds(listOf(기본_상품_ID)) } returns listOf(
            상품_도메인_생성(
                id = 기본_상품_ID,
                deletedAtOrNull = ZonedDateTime.now(),
            ),
        )

        val ex = assertThrows<CoreException> {
            productService.findOrderableSnapshots(listOf(기본_상품_ID))
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
    }

    @Test
    fun `주문_스냅샷은_상품명과_가격을_반환한다`() {
        val productRepository = mockk<ProductRepository>()
        val productService = ProductService(productRepository)
        every { productRepository.findAllByIds(listOf(기본_상품_ID)) } returns listOf(
            상품_도메인_생성(id = 기본_상품_ID),
        )

        val snapshots = productService.findOrderableSnapshots(listOf(기본_상품_ID))

        assertThat(snapshots).hasSize(1)
        assertThat(snapshots[0].productId).isEqualTo(기본_상품_ID)
        assertThat(snapshots[0].productName).isEqualTo("기본 상품")
        assertThat(snapshots[0].unitPrice).isEqualTo(10_000)
    }

    @Test
    fun `브랜드에_속한_상품들을_삭제한다`() {
        val productRepository = mockk<ProductRepository>()
        val productService = ProductService(productRepository)
        val products = listOf(
            상품_도메인_생성(id = 1L),
            상품_도메인_생성(id = 2L),
        )
        val productSlot = slot<List<ProductModel>>()
        every { productRepository.findByBrandId(10L) } returns products
        every { productRepository.saveAll(capture(productSlot)) } answers { productSlot.captured }

        val deletedProducts = productService.softDeleteByBrandId(10L)

        assertThat(deletedProducts).hasSize(2)
        assertThat(deletedProducts).allMatch { it.deletedAtOrNull != null }
        verify(exactly = 1) { productRepository.saveAll(any()) }
    }

    @Test
    fun `상품_서비스는_좋아요순_정렬조건을_저장소에_전달한다`() {
        val productRepository = mockk<ProductRepository>()
        val productService = ProductService(productRepository)
        val command = ProductSearchCommand.of(sort = "likes_desc")
        val products = listOf(상품_도메인_생성(id = 기본_상품_ID))
        every { productRepository.findByCondition(any()) } returns products

        val result = productService.findProducts(command)

        assertThat(result).isEqualTo(products)
        verify(exactly = 1) {
            productRepository.findByCondition(
                withArg {
                    assertThat(it.sort).isEqualTo(command.sort)
                },
            )
        }
    }
}
