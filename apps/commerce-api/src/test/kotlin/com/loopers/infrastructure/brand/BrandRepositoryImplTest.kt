package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BrandRepositoryImplTest @Autowired constructor(
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("여러 ID를 IN 조회하면 활성 브랜드만 반환한다 — 삭제·미존재 ID는 제외.")
    @Test
    fun findsActiveBrandsByIdsExcludingDeletedAndMissing() {
        // arrange
        val nike = brandRepository.save(BrandModel(name = "Nike", description = "Shoes"))
        val adidas = brandRepository.save(BrandModel(name = "Adidas", description = "Shoes"))
        val deleted = brandRepository.save(BrandModel(name = "Deleted", description = "Shoes").also { it.softDelete() })

        // act
        val brands = brandRepository.findActiveAllByIds(listOf(nike.id, adidas.id, deleted.id, 999_999L))

        // assert
        assertThat(brands.map { it.id }).containsExactlyInAnyOrder(nike.id, adidas.id)
    }

    @DisplayName("빈 ID 목록은 빈 결과를 반환한다.")
    @Test
    fun returnsEmptyForEmptyIds() {
        // arrange & act
        val brands = brandRepository.findActiveAllByIds(emptyList())

        // assert
        assertThat(brands).isEmpty()
    }
}
