package com.loopers.application.catalog

import com.loopers.application.brand.BrandService
import com.loopers.application.brand.dto.BrandCreateCommand
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class AdminCatalogFacadeTest {
    @DisplayName("관리자 브랜드 등록")
    @Nested
    inner class CreateBrand {
        @DisplayName("브랜드 등록 요청이 유효하면 브랜드를 저장한다")
        @Test
        fun savesBrand_whenCommandIsValid() {
            val brandRepository = FakeBrandRepository()
            val adminCatalogFacade = AdminCatalogFacade(BrandService(brandRepository))
            val command = BrandCreateCommand(
                name = "loopers",
                description = "loopers brand",
                logoImageUrl = "https://image.loopers/logo.png",
            )

            val result = adminCatalogFacade.createBrand(command)

            assertAll(
                { assertThat(result.brandId).isEqualTo(1L) },
                { assertThat(result.name).isEqualTo(command.name) },
                { assertThat(brandRepository.brands).hasSize(1) },
            )
        }

        @DisplayName("브랜드명이 비어 있으면 브랜드를 등록할 수 없다")
        @Test
        fun throwsBadRequest_whenBrandNameIsBlank() {
            val brandRepository = FakeBrandRepository()
            val adminCatalogFacade = AdminCatalogFacade(BrandService(brandRepository))

            val result = assertThrows<CoreException> {
                adminCatalogFacade.createBrand(
                    BrandCreateCommand(
                        name = "",
                        description = "loopers brand",
                        logoImageUrl = "https://image.loopers/logo.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이미 존재하는 브랜드명으로 브랜드를 등록할 수 없다")
        @Test
        fun throwsConflict_whenBrandNameAlreadyExists() {
            val brandRepository = FakeBrandRepository()
            val adminCatalogFacade = AdminCatalogFacade(BrandService(brandRepository))
            val command = BrandCreateCommand(
                name = "loopers",
                description = "loopers brand",
                logoImageUrl = "https://image.loopers/logo.png",
            )
            adminCatalogFacade.createBrand(command)

            val result = assertThrows<CoreException> {
                adminCatalogFacade.createBrand(command)
            }

            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(brandRepository.brands).hasSize(1) },
            )
        }
    }

    private class FakeBrandRepository : BrandRepository {
        val brands = mutableListOf<Brand>()

        override fun findById(brandId: Long): Brand? {
            return brands.find { it.id == brandId }
        }

        override fun existsByName(name: String): Boolean {
            return brands.any { it.name == name }
        }

        override fun save(brand: Brand): Brand {
            val savedBrand = if (brand.id == 0L) {
                Brand(
                    id = (brands.size + 1).toLong(),
                    name = brand.name,
                    description = brand.description,
                    logoImageUrl = brand.logoImageUrl,
                    isDeleted = brand.isDeleted,
                )
            } else {
                brand
            }
            brands.removeIf { it.id == savedBrand.id }
            brands.add(savedBrand)
            return savedBrand
        }
    }
}
