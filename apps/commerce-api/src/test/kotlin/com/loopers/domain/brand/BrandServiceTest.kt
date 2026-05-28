package com.loopers.domain.brand

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

class BrandServiceTest {

    private lateinit var brandRepositoryPort: BrandRepositoryPort
    private lateinit var brandService: BrandService

    @BeforeEach
    fun setUp() {
        brandRepositoryPort = mockk()
        brandService = BrandService(brandRepositoryPort)
    }

    @DisplayName("getById를 호출할 때, ")
    @Nested
    inner class GetById {
        @DisplayName("Brand가 존재하면, 도메인 객체를 반환한다.")
        @Test
        fun returnsBrand_whenExists() {
            val brand = Brand(id = 1L, name = "Nike", description = "Just do it")
            every { brandRepositoryPort.findById(1L) } returns brand

            val result = brandService.getById(1L)

            assertThat(result).isEqualTo(brand)
        }

        @DisplayName("Brand가 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            every { brandRepositoryPort.findById(any()) } returns null

            val result = assertThrows<CoreException> { brandService.getById(9999L) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("create를 호출할 때, ")
    @Nested
    inner class Create {
        @DisplayName("name이 중복되지 않으면, save한 결과를 반환한다.")
        @Test
        fun savesBrand_whenNameUnique() {
            every { brandRepositoryPort.existsByName("Nike") } returns false
            val saved = Brand(id = 1L, name = "Nike", description = "Just do it")
            val capturedBrand = slot<Brand>()
            every { brandRepositoryPort.save(capture(capturedBrand)) } returns saved

            val result = brandService.create(name = "Nike", description = "Just do it")

            assertThat(result).isEqualTo(saved)
            assertThat(capturedBrand.captured.name).isEqualTo("Nike")
            assertThat(capturedBrand.captured.id).isEqualTo(0L)
            verify(exactly = 1) { brandRepositoryPort.save(any()) }
        }

        @DisplayName("name이 이미 존재하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenNameDuplicate() {
            every { brandRepositoryPort.existsByName("Nike") } returns true

            val result = assertThrows<CoreException> {
                brandService.create(name = "Nike", description = "Just do it")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
            verify(exactly = 0) { brandRepositoryPort.save(any()) }
        }
    }

    @DisplayName("update를 호출할 때, ")
    @Nested
    inner class Update {
        @DisplayName("존재하고 name이 중복되지 않으면, save를 호출하여 결과를 반환한다.")
        @Test
        fun updatesBrand_whenValid() {
            val existing = Brand(id = 1L, name = "Nike", description = "old")
            every { brandRepositoryPort.findById(1L) } returns existing
            every { brandRepositoryPort.existsByNameAndIdNot("Nike2", 1L) } returns false
            val updated = Brand(id = 1L, name = "Nike2", description = "new")
            val captured = slot<Brand>()
            every { brandRepositoryPort.save(capture(captured)) } returns updated

            val result = brandService.update(id = 1L, name = "Nike2", description = "new")

            assertThat(result).isEqualTo(updated)
            assertThat(captured.captured.id).isEqualTo(1L)
            assertThat(captured.captured.name).isEqualTo("Nike2")
            verify(exactly = 1) { brandRepositoryPort.save(any()) }
        }

        @DisplayName("존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            every { brandRepositoryPort.findById(any()) } returns null

            val result = assertThrows<CoreException> {
                brandService.update(id = 9999L, name = "x", description = "y")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
            verify(exactly = 0) { brandRepositoryPort.save(any()) }
        }

        @DisplayName("다른 브랜드와 name이 충돌하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenDuplicateName() {
            val existing = Brand(id = 1L, name = "Nike", description = "old")
            every { brandRepositoryPort.findById(1L) } returns existing
            every { brandRepositoryPort.existsByNameAndIdNot("Adidas", 1L) } returns true

            val result = assertThrows<CoreException> {
                brandService.update(id = 1L, name = "Adidas", description = "x")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
            verify(exactly = 0) { brandRepositoryPort.save(any()) }
        }
    }

    @DisplayName("delete를 호출할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("존재하면, 브랜드를 delete한다.")
        @Test
        fun deletesBrand_whenExists() {
            val brand = Brand(id = 1L, name = "Nike", description = "x")
            every { brandRepositoryPort.findById(1L) } returns brand
            every { brandRepositoryPort.delete(brand) } returns Unit

            brandService.delete(1L)

            verify(exactly = 1) { brandRepositoryPort.delete(brand) }
        }

        @DisplayName("존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            every { brandRepositoryPort.findById(any()) } returns null

            val result = assertThrows<CoreException> { brandService.delete(9999L) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
            verify(exactly = 0) { brandRepositoryPort.delete(any()) }
        }
    }

    @DisplayName("getAll을 호출할 때, ")
    @Nested
    inner class GetAll {
        @DisplayName("Port의 findAll 결과를 그대로 반환한다.")
        @Test
        fun returnsPageResult() {
            val pageReq = PageRequest(page = 0, size = 10)
            val expected = PageResult(items = emptyList<Brand>(), page = 0, size = 10, totalElements = 0L, totalPages = 0)
            every { brandRepositoryPort.findAll(pageReq) } returns expected

            val result = brandService.getAll(pageReq)

            assertThat(result).isEqualTo(expected)
        }
    }
}
