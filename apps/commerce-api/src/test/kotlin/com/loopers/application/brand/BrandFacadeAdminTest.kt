package com.loopers.application.brand

import com.loopers.application.brand.dto.BrandCreateCommand
import com.loopers.application.brand.dto.BrandUpdateCommand
import com.loopers.application.product.ProductService
import com.loopers.domain.brand.model.Brand
import com.loopers.domain.brand.repository.BrandRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.domain.product.model.Product
import com.loopers.domain.product.repository.ProductRepository
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

class BrandFacadeAdminTest {
    @DisplayName("관리자 브랜드 삭제")
    @Nested
    inner class DeleteBrand {
        @DisplayName("브랜드를 삭제하면 해당 브랜드 상품도 삭제한다")
        @Test
        fun deletesBrandAndProducts() {
            val brandRepository = FakeBrandRepository()
            val productRepository = FakeProductRepository()
            val adminBrandFacade = createFacade(brandRepository, productRepository)
            brandRepository.save(
                Brand(
                    id = 1L,
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                ),
            )
            productRepository.save(createProduct(id = 10L, brandId = 1L))
            productRepository.save(createProduct(id = 20L, brandId = 2L))

            adminBrandFacade.deleteBrand(1L)

            assertAll(
                { assertThat(brandRepository.brands.find { it.id == 1L }?.isDeleted).isTrue() },
                { assertThat(productRepository.products.find { it.id == 10L }?.isDeleted).isTrue() },
                { assertThat(productRepository.findById(20L)?.isDeleted).isFalse() },
            )
        }

        @DisplayName("존재하지 않는 브랜드는 삭제할 수 없다")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)

            val result = assertThrows<CoreException> {
                adminBrandFacade.deleteBrand(1L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드는 삭제할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)
            brandRepository.save(
                Brand(
                    id = 1L,
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                    isDeleted = true,
                ),
            )

            val result = assertThrows<CoreException> {
                adminBrandFacade.deleteBrand(1L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("관리자 브랜드 수정")
    @Nested
    inner class UpdateBrand {
        @DisplayName("브랜드 수정 요청이 유효하면 브랜드 정보를 수정한다")
        @Test
        fun updatesBrand_whenCommandIsValid() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)
            val brand = brandRepository.save(
                Brand(
                    id = 1L,
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                ),
            )

            val result = adminBrandFacade.updateBrand(
                brandId = brand.id,
                command = BrandUpdateCommand(
                    name = "loopers updated",
                    description = "updated brand",
                    logoImageUrl = "https://image.loopers/updated.png",
                ),
            )

            assertAll(
                { assertThat(result.brandId).isEqualTo(brand.id) },
                { assertThat(result.name).isEqualTo("loopers updated") },
                { assertThat(result.description).isEqualTo("updated brand") },
                { assertThat(result.logoImageUrl).isEqualTo("https://image.loopers/updated.png") },
            )
        }

        @DisplayName("존재하지 않는 브랜드는 수정할 수 없다")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)

            val result = assertThrows<CoreException> {
                adminBrandFacade.updateBrand(
                    brandId = 1L,
                    command = BrandUpdateCommand(
                        name = "loopers",
                        description = "loopers brand",
                        logoImageUrl = "https://image.loopers/logo.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드는 수정할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)
            brandRepository.save(
                Brand(
                    id = 1L,
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                    isDeleted = true,
                ),
            )

            val result = assertThrows<CoreException> {
                adminBrandFacade.updateBrand(
                    brandId = 1L,
                    command = BrandUpdateCommand(
                        name = "loopers updated",
                        description = "updated brand",
                        logoImageUrl = "https://image.loopers/updated.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("이미 존재하는 브랜드명으로 브랜드를 수정할 수 없다")
        @Test
        fun throwsConflict_whenBrandNameAlreadyExists() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)
            brandRepository.save(
                Brand(
                    id = 1L,
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                ),
            )
            brandRepository.save(
                Brand(
                    id = 2L,
                    name = "street",
                    description = "street brand",
                    logoImageUrl = "https://image.loopers/street.png",
                ),
            )

            val result = assertThrows<CoreException> {
                adminBrandFacade.updateBrand(
                    brandId = 1L,
                    command = BrandUpdateCommand(
                        name = "street",
                        description = "updated brand",
                        logoImageUrl = "https://image.loopers/updated.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("관리자 브랜드 상세 조회")
    @Nested
    inner class GetBrand {
        @DisplayName("등록된 브랜드 상세 정보를 조회한다")
        @Test
        fun returnsBrand() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)
            val brand = brandRepository.save(
                Brand(
                    id = 1L,
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                ),
            )

            val result = adminBrandFacade.getBrand(brand.id)

            assertAll(
                { assertThat(result.brandId).isEqualTo(brand.id) },
                { assertThat(result.name).isEqualTo(brand.name) },
                { assertThat(result.description).isEqualTo(brand.description) },
                { assertThat(result.logoImageUrl).isEqualTo(brand.logoImageUrl) },
            )
        }

        @DisplayName("존재하지 않는 브랜드는 조회할 수 없다")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)

            val result = assertThrows<CoreException> {
                adminBrandFacade.getBrand(1L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드는 조회할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)
            brandRepository.save(
                Brand(
                    id = 1L,
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                    isDeleted = true,
                ),
            )

            val result = assertThrows<CoreException> {
                adminBrandFacade.getBrand(1L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("관리자 브랜드 목록 조회")
    @Nested
    inner class GetBrands {
        @DisplayName("등록된 브랜드 목록을 페이지로 조회한다")
        @Test
        fun returnsBrandPage() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)
            adminBrandFacade.createBrand(
                BrandCreateCommand(
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                ),
            )
            adminBrandFacade.createBrand(
                BrandCreateCommand(
                    name = "street",
                    description = "street brand",
                    logoImageUrl = "https://image.loopers/street.png",
                ),
            )

            val result = adminBrandFacade.getBrands(page = 0, size = 20)

            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.totalElements).isEqualTo(2L) },
                { assertThat(result.content.map { it.name }).containsExactly("street", "loopers") },
            )
        }
    }

    @DisplayName("관리자 브랜드 등록")
    @Nested
    inner class CreateBrand {
        @DisplayName("브랜드 등록 요청이 유효하면 브랜드를 저장한다")
        @Test
        fun savesBrand_whenCommandIsValid() {
            val brandRepository = FakeBrandRepository()
            val adminBrandFacade = createFacade(brandRepository)
            val command = BrandCreateCommand(
                name = "loopers",
                description = "loopers brand",
                logoImageUrl = "https://image.loopers/logo.png",
            )

            val result = adminBrandFacade.createBrand(command)

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
            val adminBrandFacade = createFacade(brandRepository)

            val result = assertThrows<CoreException> {
                adminBrandFacade.createBrand(
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
            val adminBrandFacade = createFacade(brandRepository)
            val command = BrandCreateCommand(
                name = "loopers",
                description = "loopers brand",
                logoImageUrl = "https://image.loopers/logo.png",
            )
            adminBrandFacade.createBrand(command)

            val result = assertThrows<CoreException> {
                adminBrandFacade.createBrand(command)
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
            return brands.find { it.id == brandId && !it.isDeleted }
        }

        override fun findAllByIds(brandIds: Collection<Long>): List<Brand> {
            return brands.filter { it.id in brandIds && !it.isDeleted }
        }

        override fun findDisplayable(page: Int, size: Int): Page<Brand> {
            val content = brands
                .filter { !it.isDeleted }
                .sortedByDescending { it.id }
                .drop(page * size)
                .take(size)

            return PageImpl(
                content,
                PageRequest.of(page, size),
                brands.count { !it.isDeleted }.toLong(),
            )
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

        override fun update(brand: Brand): Brand {
            brands.removeIf { it.id == brand.id }
            brands.add(brand)
            return brand
        }
    }

    private class FakeProductRepository : ProductRepository {
        val products = mutableListOf<Product>()

        override fun findById(productId: Long): Product? {
            return products.find { it.id == productId && !it.isDeleted }
        }

        override fun findAllByIds(productIds: Collection<Long>): List<Product> {
            return products.filter { it.id in productIds && !it.isDeleted }
        }

        override fun findAllByBrandId(brandId: Long): List<Product> {
            return products.filter { it.brandId == brandId && !it.isDeleted }
        }

        override fun findDisplayableSummaries(
            brandId: Long?,
            sort: ProductSort,
            page: Int,
            size: Int,
        ): Page<ProductSummary> {
            return PageImpl(emptyList(), PageRequest.of(page, size), 0)
        }

        override fun save(product: Product): Product {
            products.removeIf { it.id == product.id }
            products.add(product)
            return product
        }

        override fun existsByBrandIdAndName(brandId: Long, name: String): Boolean {
            return products.any { it.brandId == brandId && it.name == name }
        }

        override fun existsByBrandIdAndNameAndIdNot(brandId: Long, name: String, productId: Long): Boolean {
            return products.any { it.brandId == brandId && it.name == name && it.id != productId }
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

    private fun createFacade(
        brandRepository: FakeBrandRepository,
        productRepository: FakeProductRepository = FakeProductRepository(),
    ): BrandFacade {
        return BrandFacade(
            brandService = BrandService(brandRepository),
            productService = ProductService(productRepository),
        )
    }

    private fun createProduct(
        id: Long,
        brandId: Long,
    ): Product {
        return Product(
            id = id,
            brandId = brandId,
            name = "loopers hoodie",
            price = 10_000L,
            description = "loopers product",
            imageUrl = "https://image.loopers/product.png",
        )
    }
}
