package com.loopers.application.like

import com.loopers.application.product.ProductService
import com.loopers.application.productstat.ProductStatService
import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.domain.productstat.ProductStat
import com.loopers.domain.productstat.ProductStatRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class LikeFacadeTest {
    @DisplayName("상품 좋아요 등록")
    @Nested
    inner class LikeProduct {
        @DisplayName("좋아요 상태가 아니면 좋아요를 등록하고 좋아요 수를 증가시킨다")
        @Test
        fun likesProduct() {
            val fixture = LikeFacadeFixture()
            fixture.productRepository.save(createProduct(id = 10L))

            fixture.likeFacade.like(memberId = 1L, productId = 10L)

            val productStat = fixture.productStatRepository.findByProductId(10L)
            assertAll(
                { assertThat(fixture.likeRepository.exists(memberId = 1L, productId = 10L)).isTrue() },
                { assertThat(productStat?.likeCount).isEqualTo(1L) },
            )
        }

        @DisplayName("이미 좋아요 상태면 성공하되 좋아요 수를 증가시키지 않는다")
        @Test
        fun ignoresDuplicateLike() {
            val fixture = LikeFacadeFixture()
            fixture.productRepository.save(createProduct(id = 10L))
            fixture.productStatRepository.save(ProductStat(productId = 10L, likeCount = 1L))
            fixture.likeRepository.saveIfAbsent(Like(memberId = 1L, productId = 10L))

            fixture.likeFacade.like(memberId = 1L, productId = 10L)

            val productStat = fixture.productStatRepository.findByProductId(10L)
            assertThat(productStat?.likeCount).isEqualTo(1L)
        }

        @DisplayName("존재하지 않는 상품은 좋아요할 수 없다")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            val fixture = LikeFacadeFixture()

            val result = assertThrows<CoreException> {
                fixture.likeFacade.like(memberId = 1L, productId = 10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 좋아요할 수 없다")
        @Test
        fun throwsNotFound_whenProductIsDeleted() {
            val fixture = LikeFacadeFixture()
            fixture.productRepository.save(createProduct(id = 10L, isDeleted = true))

            val result = assertThrows<CoreException> {
                fixture.likeFacade.like(memberId = 1L, productId = 10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("상품 좋아요 취소")
    @Nested
    inner class UnlikeProduct {
        @DisplayName("좋아요 상태면 좋아요를 삭제하고 좋아요 수를 감소시킨다")
        @Test
        fun unlikesProduct() {
            val fixture = LikeFacadeFixture()
            fixture.productRepository.save(createProduct(id = 10L))
            fixture.productStatRepository.save(ProductStat(productId = 10L, likeCount = 1L))
            fixture.likeRepository.saveIfAbsent(Like(memberId = 1L, productId = 10L))

            fixture.likeFacade.unlike(memberId = 1L, productId = 10L)

            val productStat = fixture.productStatRepository.findByProductId(10L)
            assertAll(
                { assertThat(fixture.likeRepository.exists(memberId = 1L, productId = 10L)).isFalse() },
                { assertThat(productStat?.likeCount).isEqualTo(0L) },
            )
        }

        @DisplayName("이미 좋아요하지 않은 상태면 성공하되 좋아요 수를 감소시키지 않는다")
        @Test
        fun ignoresAbsentLike() {
            val fixture = LikeFacadeFixture()
            fixture.productRepository.save(createProduct(id = 10L))
            fixture.productStatRepository.save(ProductStat(productId = 10L, likeCount = 1L))

            fixture.likeFacade.unlike(memberId = 1L, productId = 10L)

            val productStat = fixture.productStatRepository.findByProductId(10L)
            assertThat(productStat?.likeCount).isEqualTo(1L)
        }

        @DisplayName("존재하지 않는 상품은 좋아요 취소할 수 없다")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            val fixture = LikeFacadeFixture()

            val result = assertThrows<CoreException> {
                fixture.likeFacade.unlike(memberId = 1L, productId = 10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 좋아요 취소할 수 없다")
        @Test
        fun throwsNotFound_whenProductIsDeleted() {
            val fixture = LikeFacadeFixture()
            fixture.productRepository.save(createProduct(id = 10L, isDeleted = true))

            val result = assertThrows<CoreException> {
                fixture.likeFacade.unlike(memberId = 1L, productId = 10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    private class LikeFacadeFixture {
        val likeRepository = FakeLikeRepository()
        val productRepository = FakeProductRepository()
        val productStatRepository = FakeProductStatRepository()
        val likeFacade = LikeFacade(
            likeService = LikeService(likeRepository),
            productService = ProductService(productRepository),
            productStatService = ProductStatService(productStatRepository),
            productLikeService = ProductLikeService(),
        )
    }

    private class FakeLikeRepository : LikeRepository {
        private val likes = mutableListOf<Like>()

        override fun saveIfAbsent(like: Like): Boolean {
            if (exists(memberId = like.memberId, productId = like.productId)) {
                return false
            }

            likes.add(like)
            return true
        }

        override fun deleteIfExists(memberId: Long, productId: Long): Boolean {
            return likes.removeIf { it.memberId == memberId && it.productId == productId }
        }

        fun exists(memberId: Long, productId: Long): Boolean {
            return likes.any { it.memberId == memberId && it.productId == productId }
        }
    }

    private class FakeProductRepository : ProductRepository {
        private val products = mutableListOf<Product>()

        override fun findById(productId: Long): Product? {
            return products.find { it.id == productId }
        }

        override fun findAllByBrandId(brandId: Long): List<Product> {
            return products.filter { it.brandId == brandId }
        }

        override fun findDisplayableSummaries(
            brandId: Long?,
            sort: ProductSort,
            page: Int,
            size: Int,
        ): Page<ProductSummary> {
            return PageImpl(emptyList(), PageRequest.of(page, size), 0)
        }

        override fun existsByBrandIdAndName(brandId: Long, name: String): Boolean {
            return products.any { it.brandId == brandId && it.name == name }
        }

        override fun existsByBrandIdAndNameAndIdNot(brandId: Long, name: String, productId: Long): Boolean {
            return products.any { it.brandId == brandId && it.name == name && it.id != productId }
        }

        override fun save(product: Product): Product {
            products.removeIf { it.id == product.id }
            products.add(product)
            return product
        }

        override fun update(product: Product): Product {
            products.removeIf { it.id == product.id }
            products.add(product)
            return product
        }

        override fun updateAll(products: Collection<Product>): List<Product> {
            products.forEach(::save)
            return products.toList()
        }
    }

    private class FakeProductStatRepository : ProductStatRepository {
        private val productStats = mutableListOf<ProductStat>()

        override fun findByProductId(productId: Long): ProductStat? {
            return productStats.find { it.productId == productId }
        }

        override fun findAllByProductIds(productIds: Collection<Long>): List<ProductStat> {
            return productStats.filter { it.productId in productIds }
        }

        override fun save(productStat: ProductStat): ProductStat {
            productStats.removeIf { it.productId == productStat.productId }
            productStats.add(productStat)
            return productStat
        }
    }

    private fun createProduct(
        id: Long,
        isDeleted: Boolean = false,
    ): Product {
        return Product(
            id = id,
            brandId = 1L,
            name = "loopers hoodie",
            price = 10_000L,
            description = "loopers product",
            imageUrl = "https://image.loopers/product.png",
            isDeleted = isDeleted,
        )
    }
}
