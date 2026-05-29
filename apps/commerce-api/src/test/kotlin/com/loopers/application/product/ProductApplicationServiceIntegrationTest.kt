package com.loopers.application.product

import com.loopers.domain.product.ProductPrice
import com.loopers.domain.product.ProductSearchCondition
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockQuantity
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.paging.PageCondition
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class ProductApplicationServiceIntegrationTest @Autowired constructor(
    private val productApplicationService: ProductApplicationService,
    private val productJpaRepository: ProductJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품 생성 시, ")
    @Nested
    inner class CreateProduct {
        @DisplayName("유효한 값이면 상품을 저장한다.")
        @Test
        fun createProduct_whenAllFieldsAreValid() {
            // act
            val product = productApplicationService.createProduct(
                brandId = 1L,
                name = "Loopers T-Shirt",
                description = "매일 입기 좋은 티셔츠",
                price = ProductPrice(10_000L),
                stock = Stock(10),
            )

            // assert
            assertAll(
                { assertThat(product.id).isNotNull() },
                { assertThat(product.brandId).isEqualTo(1L) },
                { assertThat(product.name).isEqualTo("Loopers T-Shirt") },
                { assertThat(product.description).isEqualTo("매일 입기 좋은 티셔츠") },
                { assertThat(product.price).isEqualTo(ProductPrice(10_000L)) },
                { assertThat(product.stock).isEqualTo(Stock(10)) },
            )
        }
    }

    @DisplayName("상품 조회 시, ")
    @Nested
    inner class GetProduct {
        @DisplayName("존재하는 상품 ID이면 상품을 반환한다.")
        @Test
        fun getProduct_whenProductExists() {
            // arrange
            val entity = productJpaRepository.save(newProductJpaEntity(name = "Loopers T-Shirt"))

            // act
            val product = productApplicationService.getProduct(entity.id)

            // assert
            assertAll(
                { assertThat(product.id).isEqualTo(entity.id) },
                { assertThat(product.name).isEqualTo("Loopers T-Shirt") },
            )
        }

        @DisplayName("존재하지 않는 상품 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // act & assert
            val result = assertThrows<CoreException> {
                productApplicationService.getProduct(999L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("상품 목록 조회 시, ")
    @Nested
    inner class GetProducts {
        @DisplayName("삭제되지 않은 상품만 가격 오름차순으로 조회한다.")
        @Test
        fun getProducts_orderByPriceAsc() {
            // arrange
            productJpaRepository.save(newProductJpaEntity(name = "B", price = 20_000L))
            productJpaRepository.save(newProductJpaEntity(name = "A", price = 10_000L))
            val deleted = productJpaRepository.save(newProductJpaEntity(name = "Deleted", price = 1_000L))
            deleted.delete()
            productJpaRepository.save(deleted)

            // act
            val result = productApplicationService.getProducts(
                ProductSearchCondition(
                    sortType = ProductSortType.PRICE_ASC,
                    pageCondition = PageCondition(page = 0, size = 10),
                ),
            )

            // assert
            assertAll(
                { assertThat(result.items.map { it.name }).containsExactly("A", "B") },
                { assertThat(result.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("브랜드 ID 조건이 있으면 해당 브랜드 상품만 조회한다.")
        @Test
        fun getProducts_filterByBrandId() {
            // arrange
            productJpaRepository.save(newProductJpaEntity(brandId = 1L, name = "A"))
            productJpaRepository.save(newProductJpaEntity(brandId = 2L, name = "B"))

            // act
            val result = productApplicationService.getProducts(
                ProductSearchCondition(
                    brandId = 2L,
                    pageCondition = PageCondition(page = 0, size = 10),
                ),
            )

            // assert
            assertThat(result.items.map { it.name }).containsExactly("B")
        }

        @DisplayName("최신순이면 생성일 내림차순으로 조회한다.")
        @Test
        fun getProducts_orderByLatest() {
            // arrange
            productJpaRepository.save(newProductJpaEntity(name = "First"))
            productJpaRepository.save(newProductJpaEntity(name = "Second"))
            productJpaRepository.save(newProductJpaEntity(name = "Third"))

            // act
            val result = productApplicationService.getProducts(
                ProductSearchCondition(
                    sortType = ProductSortType.LATEST,
                    pageCondition = PageCondition(page = 0, size = 10),
                ),
            )

            // assert
            assertThat(result.items.map { it.name }).containsExactly("Third", "Second", "First")
        }

        @DisplayName("좋아요순이면 좋아요 수 내림차순으로 조회한다.")
        @Test
        fun getProducts_orderByLikesDesc() {
            // arrange
            productJpaRepository.save(newProductJpaEntity(name = "A", likeCount = 1))
            productJpaRepository.save(newProductJpaEntity(name = "B", likeCount = 3))
            productJpaRepository.save(newProductJpaEntity(name = "C", likeCount = 2))

            // act
            val result = productApplicationService.getProducts(
                ProductSearchCondition(
                    sortType = ProductSortType.LIKES_DESC,
                    pageCondition = PageCondition(page = 0, size = 10),
                ),
            )

            // assert
            assertThat(result.items.map { it.name }).containsExactly("B", "C", "A")
        }
    }

    @DisplayName("상품 수정 시, ")
    @Nested
    inner class UpdateProduct {
        @DisplayName("유효한 값이면 상품 정보를 수정한다.")
        @Test
        fun updateProduct_whenAllFieldsAreValid() {
            // arrange
            val entity = productJpaRepository.save(newProductJpaEntity())

            // act
            val product = productApplicationService.updateProduct(
                id = entity.id,
                name = "Loopers Hoodie",
                description = "따뜻한 후드",
                price = ProductPrice(30_000L),
                stock = Stock(5),
            )

            // assert
            assertAll(
                { assertThat(product.id).isEqualTo(entity.id) },
                { assertThat(product.name).isEqualTo("Loopers Hoodie") },
                { assertThat(product.description).isEqualTo("따뜻한 후드") },
                { assertThat(product.price).isEqualTo(ProductPrice(30_000L)) },
                { assertThat(product.stock).isEqualTo(Stock(5)) },
            )
        }
    }

    @DisplayName("상품 삭제 시, ")
    @Nested
    inner class DeleteProduct {
        @DisplayName("존재하는 상품 ID이면 soft delete 처리하고 기본 조회에서 제외한다.")
        @Test
        fun deleteProduct_whenProductExists() {
            // arrange
            val entity = productJpaRepository.save(newProductJpaEntity())

            // act
            productApplicationService.deleteProduct(entity.id)

            // assert
            val deletedEntity = productJpaRepository.findByIdOrNull(entity.id)
            val result = assertThrows<CoreException> {
                productApplicationService.getProduct(entity.id)
            }
            assertAll(
                { assertThat(deletedEntity?.deletedAt).isNotNull() },
                { assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND) },
            )
        }
    }

    @DisplayName("상품 재고와 좋아요 수 변경 시, ")
    @Nested
    inner class ChangeProductState {
        @DisplayName("재고를 차감하고 복구할 수 있다.")
        @Test
        fun deductAndRestoreStock() {
            // arrange
            val entity = productJpaRepository.save(newProductJpaEntity(stock = 10))

            // act
            val deducted = productApplicationService.deductStock(entity.id, StockQuantity(3))
            val restored = productApplicationService.restoreStock(entity.id, StockQuantity(2))

            // assert
            assertAll(
                { assertThat(deducted.stock).isEqualTo(Stock(7)) },
                { assertThat(restored.stock).isEqualTo(Stock(9)) },
            )
        }

        @DisplayName("재고가 1개일 때 동시에 차감 요청이 들어오면 한 번만 성공한다.")
        @Test
        fun deductStock_succeedsOnlyOnce_whenConcurrentRequestsExceedStock() {
            // arrange
            val entity = productJpaRepository.save(newProductJpaEntity(stock = 1))

            // act
            val results = runConcurrentlyCatching(10) {
                productApplicationService.deductStock(entity.id, StockQuantity(1))
            }

            // assert
            val updated = productApplicationService.getProduct(entity.id)
            assertAll(
                { assertThat(results.count { it.isSuccess }).isEqualTo(1) },
                { assertThat(results.count { it.isFailure }).isEqualTo(9) },
                { assertThat(updated.stock).isEqualTo(Stock(0)) },
            )
        }

        @DisplayName("좋아요 수를 증가하고 감소할 수 있다.")
        @Test
        fun increaseAndDecreaseLikeCount() {
            // arrange
            val entity = productJpaRepository.save(newProductJpaEntity(likeCount = 1))

            // act
            val increased = productApplicationService.increaseLikeCount(entity.id)
            val decreased = productApplicationService.decreaseLikeCount(entity.id)

            // assert
            assertAll(
                { assertThat(increased.likeCount).isEqualTo(2) },
                { assertThat(decreased.likeCount).isEqualTo(1) },
        )
    }

    private fun <T> runConcurrentlyCatching(times: Int, task: () -> T): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(times)
        val ready = CountDownLatch(times)
        val start = CountDownLatch(1)

        return try {
            val futures = (1..times).map {
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching { task() }
                    },
                )
            }
            ready.await()
            start.countDown()
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }
}

    private fun newProductJpaEntity(
        brandId: Long = 1L,
        name: String = "Loopers T-Shirt",
        description: String = "매일 입기 좋은 티셔츠",
        price: Long = 10_000L,
        stock: Int = 10,
        likeCount: Int = 0,
    ) = ProductJpaEntity(
        brandId = brandId,
        name = name,
        description = description,
        price = price,
        stock = stock,
        likeCount = likeCount,
    )
}
