package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BrandFacadeTest {

    private lateinit var brandRepositoryPort: BrandRepositoryPort
    private lateinit var brandFacade: BrandFacade

    @BeforeEach
    fun setUp() {
        brandRepositoryPort = mockk()
        brandFacade = BrandFacade(brandRepositoryPort)
    }

    @DisplayName("getBrand를 호출할 때, ")
    @Nested
    inner class GetBrand {
        @DisplayName("Brand가 존재하면, 도메인 객체를 반환한다.")
        @Test
        fun returnsBrand_whenExists() {
            // arrange
            val brand = Brand(id = 1L, name = "Nike", description = "Just do it")
            every { brandRepositoryPort.findByIdOrNull(1L) } returns brand

            // act
            val result = brandFacade.getBrand(1L)

            // assert
            assertThat(result).isEqualTo(brand)
        }

        @DisplayName("Brand가 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            // arrange
            every { brandRepositoryPort.findByIdOrNull(any()) } returns null

            // act
            val result = assertThrows<CoreException> { brandFacade.getBrand(9999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("createBrand를 호출할 때, ")
    @Nested
    inner class CreateBrand {
        @DisplayName("name이 중복되지 않으면, save한 결과를 반환한다.")
        @Test
        fun savesBrand_whenNameUnique() {
            // arrange
            every { brandRepositoryPort.existsByName("Nike") } returns false
            val saved = Brand(id = 1L, name = "Nike", description = "Just do it")
            val capturedBrand = slot<Brand>()
            every { brandRepositoryPort.save(capture(capturedBrand)) } returns saved

            // act
            val result = brandFacade.createBrand(CreateBrandCommand(name = "Nike", description = "Just do it"))

            // assert
            assertThat(result).isEqualTo(saved)
            assertThat(capturedBrand.captured.name).isEqualTo("Nike")
            assertThat(capturedBrand.captured.id).isEqualTo(0L)
            verify(exactly = 1) { brandRepositoryPort.save(any()) }
        }

        @DisplayName("name이 이미 존재하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenNameDuplicate() {
            // arrange
            every { brandRepositoryPort.existsByName("Nike") } returns true

            // act
            val result = assertThrows<CoreException> {
                brandFacade.createBrand(CreateBrandCommand(name = "Nike", description = "Just do it"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
            verify(exactly = 0) { brandRepositoryPort.save(any()) }
        }
    }

    @DisplayName("updateBrand를 호출할 때, ")
    @Nested
    inner class UpdateBrand {
        @DisplayName("존재하고 name이 중복되지 않으면, save를 호출하여 결과를 반환한다.")
        @Test
        fun updatesBrand_whenValid() {
            // arrange
            val existing = Brand(id = 1L, name = "Nike", description = "old")
            every { brandRepositoryPort.findByIdOrNull(1L) } returns existing
            every { brandRepositoryPort.existsByNameAndIdNot("Nike2", 1L) } returns false
            val updated = Brand(id = 1L, name = "Nike2", description = "new")
            val captured = slot<Brand>()
            every { brandRepositoryPort.save(capture(captured)) } returns updated

            // act
            val result = brandFacade.updateBrand(UpdateBrandCommand(id = 1L, name = "Nike2", description = "new"))

            // assert
            assertThat(result).isEqualTo(updated)
            assertThat(captured.captured.id).isEqualTo(1L)
            assertThat(captured.captured.name).isEqualTo("Nike2")
            verify(exactly = 1) { brandRepositoryPort.save(any()) }
        }

        @DisplayName("존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            // arrange
            every { brandRepositoryPort.findByIdOrNull(any()) } returns null

            // act
            val result = assertThrows<CoreException> {
                brandFacade.updateBrand(UpdateBrandCommand(id = 9999L, name = "x", description = "y"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
            verify(exactly = 0) { brandRepositoryPort.save(any()) }
        }

        @DisplayName("다른 브랜드와 name이 충돌하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenDuplicateName() {
            // arrange
            val existing = Brand(id = 1L, name = "Nike", description = "old")
            every { brandRepositoryPort.findByIdOrNull(1L) } returns existing
            every { brandRepositoryPort.existsByNameAndIdNot("Adidas", 1L) } returns true

            // act
            val result = assertThrows<CoreException> {
                brandFacade.updateBrand(UpdateBrandCommand(id = 1L, name = "Adidas", description = "x"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
            verify(exactly = 0) { brandRepositoryPort.save(any()) }
        }
    }

    @DisplayName("deleteBrand를 호출할 때, ")
    @Nested
    inner class DeleteBrand {
        @DisplayName("존재하면, delete를 호출한다.")
        @Test
        fun deletesBrand_whenExists() {
            // arrange
            val brand = Brand(id = 1L, name = "Nike", description = "x")
            every { brandRepositoryPort.findByIdOrNull(1L) } returns brand
            every { brandRepositoryPort.delete(brand) } returns Unit

            // act
            brandFacade.deleteBrand(1L)

            // assert
            verify(exactly = 1) { brandRepositoryPort.delete(brand) }
        }

        @DisplayName("존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            // arrange
            every { brandRepositoryPort.findByIdOrNull(any()) } returns null

            // act
            val result = assertThrows<CoreException> { brandFacade.deleteBrand(9999L) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
            verify(exactly = 0) { brandRepositoryPort.delete(any()) }
        }
    }

    @DisplayName("getBrands를 호출할 때, ")
    @Nested
    inner class GetBrands {
        @DisplayName("Port의 findAll 결과를 그대로 반환한다.")
        @Test
        fun returnsPageResult() {
            // arrange
            val pageReq = PageRequest(page = 0, size = 10)
            val expected = PageResult(items = emptyList<Brand>(), page = 0, size = 10, totalElements = 0L, totalPages = 0)
            every { brandRepositoryPort.findAll(pageReq) } returns expected

            // act
            val result = brandFacade.getBrands(pageReq)

            // assert
            assertThat(result).isEqualTo(expected)
        }
    }
}
