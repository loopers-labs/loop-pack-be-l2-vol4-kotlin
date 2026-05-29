package com.loopers.application.brand

import com.loopers.infrastructure.brand.BrandJpaEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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

@SpringBootTest
class BrandApplicationServiceIntegrationTest @Autowired constructor(
    private val brandApplicationService: BrandApplicationService,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드 생성 시, ")
    @Nested
    inner class CreateBrand {
        @DisplayName("유효한 값이면 브랜드를 저장한다.")
        @Test
        fun createBrand_whenAllFieldsAreValid() {
            // act
            val brand = brandApplicationService.createBrand(
                name = "Loopers",
                description = "감성 이커머스 브랜드",
                logoImageUrl = "https://example.com/logo.png",
            )

            // assert
            assertAll(
                { assertThat(brand.id).isNotNull() },
                { assertThat(brand.name).isEqualTo("Loopers") },
                { assertThat(brand.description).isEqualTo("감성 이커머스 브랜드") },
                { assertThat(brand.logoImageUrl).isEqualTo("https://example.com/logo.png") },
            )
        }
    }

    @DisplayName("브랜드 조회 시, ")
    @Nested
    inner class GetBrand {
        @DisplayName("존재하는 브랜드 ID이면 브랜드를 반환한다.")
        @Test
        fun getBrand_whenBrandExists() {
            // arrange
            val entity = brandJpaRepository.save(
                BrandJpaEntity(
                    name = "Loopers",
                    description = "감성 이커머스 브랜드",
                    logoImageUrl = null,
                ),
            )

            // act
            val brand = brandApplicationService.getBrand(entity.id)

            // assert
            assertAll(
                { assertThat(brand.id).isEqualTo(entity.id) },
                { assertThat(brand.name).isEqualTo("Loopers") },
                { assertThat(brand.description).isEqualTo("감성 이커머스 브랜드") },
            )
        }

        @DisplayName("존재하지 않는 브랜드 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act & assert
            val result = assertThrows<CoreException> {
                brandApplicationService.getBrand(999L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("브랜드 벌크 조회 시, ")
    @Nested
    inner class GetBrands {
        @DisplayName("전달한 ID 목록에 해당하는 삭제되지 않은 브랜드만 반환한다.")
        @Test
        fun getBrands_returnsActiveBrands() {
            // arrange
            val loopers = brandJpaRepository.save(
                BrandJpaEntity(
                    name = "Loopers",
                    description = "감성 이커머스 브랜드",
                    logoImageUrl = null,
                ),
            )
            val outer = brandJpaRepository.save(
                BrandJpaEntity(
                    name = "Outer",
                    description = "아우터 브랜드",
                    logoImageUrl = null,
                ),
            )
            val deleted = brandJpaRepository.save(
                BrandJpaEntity(
                    name = "Deleted",
                    description = "삭제된 브랜드",
                    logoImageUrl = null,
                ),
            )
            deleted.delete()
            brandJpaRepository.save(deleted)

            // act
            val brands = brandApplicationService.getBrands(listOf(loopers.id, outer.id, deleted.id))

            // assert
            assertThat(brands.map { it.name }).containsExactlyInAnyOrder("Loopers", "Outer")
        }

        @DisplayName("빈 ID 목록이면 빈 목록을 반환한다.")
        @Test
        fun getBrands_returnsEmptyList_whenIdsAreEmpty() {
            // act
            val brands = brandApplicationService.getBrands(emptyList())

            // assert
            assertThat(brands).isEmpty()
        }
    }

    @DisplayName("브랜드 수정 시, ")
    @Nested
    inner class UpdateBrand {
        @DisplayName("유효한 값이면 브랜드 정보를 수정한다.")
        @Test
        fun updateBrand_whenAllFieldsAreValid() {
            // arrange
            val entity = brandJpaRepository.save(
                BrandJpaEntity(
                    name = "Loopers",
                    description = "감성 이커머스 브랜드",
                    logoImageUrl = null,
                ),
            )

            // act
            val brand = brandApplicationService.updateBrand(
                id = entity.id,
                name = "New Loopers",
                description = "새로운 브랜드 설명",
                logoImageUrl = "https://example.com/new-logo.png",
            )

            // assert
            assertAll(
                { assertThat(brand.id).isEqualTo(entity.id) },
                { assertThat(brand.name).isEqualTo("New Loopers") },
                { assertThat(brand.description).isEqualTo("새로운 브랜드 설명") },
                { assertThat(brand.logoImageUrl).isEqualTo("https://example.com/new-logo.png") },
            )
        }
    }

    @DisplayName("브랜드 삭제 시, ")
    @Nested
    inner class DeleteBrand {
        @DisplayName("존재하는 브랜드 ID이면 soft delete 처리하고 기본 조회에서 제외한다.")
        @Test
        fun deleteBrand_whenBrandExists() {
            // arrange
            val entity = brandJpaRepository.save(
                BrandJpaEntity(
                    name = "Loopers",
                    description = "감성 이커머스 브랜드",
                    logoImageUrl = null,
                ),
            )

            // act
            brandApplicationService.deleteBrand(entity.id)

            // assert
            val deletedEntity = brandJpaRepository.findByIdOrNull(entity.id)
            val result = assertThrows<CoreException> {
                brandApplicationService.getBrand(entity.id)
            }
            assertAll(
                { assertThat(deletedEntity?.deletedAt).isNotNull() },
                { assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND) },
            )
        }
    }
}
