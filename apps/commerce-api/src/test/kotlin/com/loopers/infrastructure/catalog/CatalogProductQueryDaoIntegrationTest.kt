package com.loopers.infrastructure.catalog

import com.loopers.application.catalog.CatalogApplicationService
import com.loopers.application.catalog.ProductSort
import com.loopers.domain.catalog.CatalogCommand
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CatalogProductQueryDaoIntegrationTest @Autowired constructor(
    private val catalogApplicationService: CatalogApplicationService,
    private val catalogProductQueryDao: CatalogProductQueryDao,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("displayable 상품만 likes_desc 정렬로 조회한다.")
    @Test
    fun findsDisplayableProductsSortedByLikesDesc() {
        val brand = catalogApplicationService.createBrand(CatalogCommand.CreateBrand("Nike"))
        val first = catalogApplicationService.createProduct(CatalogCommand.CreateProduct(brand.brandId, "Low", 1000, 1, emptyList()))
        val second = catalogApplicationService.createProduct(CatalogCommand.CreateProduct(brand.brandId, "High", 2000, 1, emptyList()))
        catalogApplicationService.increaseLikeCount(second.productId)
        catalogApplicationService.suspendProduct(first.productId)

        val result = catalogProductQueryDao.findDisplayableProducts(ProductSort.LIKES_DESC, page = 0, size = 20)

        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result[0].productId).isEqualTo(second.productId) },
            { assertThat(result[0].likeCount).isEqualTo(1) },
        )
    }
}
