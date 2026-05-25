package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BrandRepositoryAdapterIntegrationTest @Autowired constructor(
    private val brandRepositoryPort: BrandRepositoryPort,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save를 호출할 때, ")
    @Nested
    inner class Save {
        @DisplayName("id가 0인 Brand를 저장하면, id가 부여되고 DB에 INSERT된다.")
        @Test
        fun insertsBrand_whenIdIsZero() {
            // act
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "Just do it"))

            // assert
            assertThat(saved.id).isGreaterThan(0L)
            assertThat(brandJpaRepository.findById(saved.id)).isPresent
        }

        @DisplayName("id가 존재하는 Brand를 저장하면, 해당 엔티티가 UPDATE된다.")
        @Test
        fun updatesBrand_whenIdExists() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "old"))

            // act
            val updated = brandRepositoryPort.save(saved.update(name = "Nike2", description = "new"))

            // assert
            assertThat(updated.id).isEqualTo(saved.id)
            assertThat(updated.name).isEqualTo("Nike2")
            assertThat(updated.description).isEqualTo("new")
            assertThat(brandJpaRepository.findById(saved.id).get().name).isEqualTo("Nike2")
        }

        @DisplayName("id가 존재하지만 DB에 없는 Brand를 저장하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenIdNotInDb() {
            // act
            val result = assertThrows<CoreException> {
                brandRepositoryPort.save(Brand(id = 9999L, name = "x", description = "y"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("findByIdOrNull을 호출할 때, ")
    @Nested
    inner class FindById {
        @DisplayName("존재하는 id로 조회하면, Brand를 반환한다.")
        @Test
        fun returnsBrand_whenExists() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "Just do it"))

            // act
            val found = brandRepositoryPort.findByIdOrNull(saved.id)

            // assert
            assertThat(found).isNotNull
            assertThat(found?.name).isEqualTo("Nike")
        }

        @DisplayName("존재하지 않는 id로 조회하면, null을 반환한다.")
        @Test
        fun returnsNull_whenMissing() {
            assertThat(brandRepositoryPort.findByIdOrNull(9999L)).isNull()
        }
    }

    @DisplayName("existsByName / existsByNameAndIdNot을 호출할 때, ")
    @Nested
    inner class Exists {
        @DisplayName("동일 name이 이미 존재하면 true, 없으면 false를 반환한다.")
        @Test
        fun existsByName_returnsCorrectly() {
            // arrange
            brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))

            // assert
            assertThat(brandRepositoryPort.existsByName("Nike")).isTrue()
            assertThat(brandRepositoryPort.existsByName("Adidas")).isFalse()
        }

        @DisplayName("같은 이름이라도 excludeId가 자기 자신이면 false를 반환한다.")
        @Test
        fun existsByNameAndIdNot_excludesSelf() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))

            // assert
            assertThat(brandRepositoryPort.existsByNameAndIdNot("Nike", saved.id)).isFalse()
            assertThat(brandRepositoryPort.existsByNameAndIdNot("Nike", 9999L)).isTrue()
        }
    }

    @DisplayName("delete를 호출할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("Brand가 soft delete되어 일반 조회로는 보이지 않는다.")
        @Test
        fun softDeletesBrand() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))

            // act
            brandRepositoryPort.delete(saved)

            // assert: @SQLRestriction 으로 findById 결과 비어 있음
            assertThat(brandJpaRepository.findById(saved.id)).isEmpty
            assertThat(brandRepositoryPort.findByIdOrNull(saved.id)).isNull()
        }
    }

    @DisplayName("findAll을 호출할 때, ")
    @Nested
    inner class FindAll {
        @DisplayName("페이지/사이즈에 맞게 결과를 반환하고, totalElements/totalPages를 채운다.")
        @Test
        fun returnsPagedResult() {
            // arrange
            repeat(5) { idx ->
                brandRepositoryPort.save(Brand.create(name = "brand-$idx", description = "d-$idx"))
            }

            // act
            val firstPage = brandRepositoryPort.findAll(PageRequest(page = 0, size = 3))
            val secondPage = brandRepositoryPort.findAll(PageRequest(page = 1, size = 3))

            // assert
            assertThat(firstPage.items).hasSize(3)
            assertThat(firstPage.totalElements).isEqualTo(5L)
            assertThat(firstPage.totalPages).isEqualTo(2)
            assertThat(secondPage.items).hasSize(2)
            assertThat(secondPage.page).isEqualTo(1)
        }
    }
}
