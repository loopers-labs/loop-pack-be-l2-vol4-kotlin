package com.loopers.infrastructure.product

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest
class ProductRepositoryImplTest @Autowired constructor(
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드 필터와 좋아요순 정렬을 Page 단위로 조회한다.")
    @Test
    fun findsActiveProductsByBrandSortedByLikesDesc() {
        // arrange
        val brand = brandRepository.save(BrandModel(name = "Nike", description = "Shoes"))
        val otherBrand = brandRepository.save(BrandModel(name = "Adidas", description = "Shoes"))
        val top = productRepository.save(product(brand.id, "Top", likeCount = 20))
        val second = productRepository.save(product(brand.id, "Second", likeCount = 10))
        productRepository.save(product(brand.id, "Third", likeCount = 1))
        productRepository.save(product(otherBrand.id, "Other", likeCount = 999))
        productRepository.save(product(brand.id, "Deleted", likeCount = 1000).also { it.softDelete() })

        // act
        val page = productRepository.findActiveAll(
            brandId = brand.id,
            sort = ProductSort.LIKES_DESC,
            pageable = ProductSort.LIKES_DESC.toPageable(page = 0, size = 2),
        )

        // assert
        assertAll(
            { assertThat(page.content.map { it.id }).containsExactly(top.id, second.id) },
            { assertThat(page.totalElements).isEqualTo(3) },
            { assertThat(page.totalPages).isEqualTo(2) },
        )
    }

    @DisplayName("브랜드 필터 없이도 활성 상품만 좋아요순으로 페이지 조회한다.")
    @Test
    fun findsActiveProductsSortedByLikesDesc() {
        // arrange
        val brand = brandRepository.save(BrandModel(name = "Nike", description = "Shoes"))
        val first = productRepository.save(product(brand.id, "First", likeCount = 30))
        val second = productRepository.save(product(brand.id, "Second", likeCount = 20))
        productRepository.save(product(brand.id, "Deleted", likeCount = 1000).also { it.softDelete() })

        // act
        val page = productRepository.findActiveAll(
            brandId = null,
            sort = ProductSort.LIKES_DESC,
            pageable = ProductSort.LIKES_DESC.toPageable(page = 0, size = 10),
        )

        // assert
        assertAll(
            { assertThat(page.content.map { it.id }).containsExactly(first.id, second.id) },
            { assertThat(page.totalElements).isEqualTo(2) },
        )
    }

    private fun product(
        brandId: Long,
        name: String,
        likeCount: Int,
    ): ProductModel {
        return ProductModel(
            brandId = brandId,
            name = name,
            description = "Description",
            price = BigDecimal("10000.00"),
            likeCount = likeCount,
        )
    }
}
