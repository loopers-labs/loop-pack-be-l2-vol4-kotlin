package com.loopers.domain.brand.unit

import com.loopers.domain.brand.model.BrandModel
import com.loopers.domain.brand.port.BrandRepository
import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.기본_브랜드_ID
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_도메인_생성
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class BrandServiceTest {
    @Test
    fun `브랜드를_등록한다`() {
        val brandRepository = mockk<BrandRepository>()
        val brandService = BrandService(brandRepository)
        val brandSlot = slot<BrandModel>()
        every { brandRepository.save(capture(brandSlot)) } answers {
            brandSlot.captured.copy(id = 기본_브랜드_ID)
        }

        val brand = brandService.register(브랜드_등록_커맨드())

        assertThat(brand.id).isEqualTo(기본_브랜드_ID)
        assertThat(brandSlot.captured.name.value).isEqualTo("기본 브랜드")
    }

    @Test
    fun `삭제된_브랜드_조회는_NOT_FOUND가_발생한다`() {
        val brandRepository = mockk<BrandRepository>()
        val brandService = BrandService(brandRepository)
        every { brandRepository.findById(기본_브랜드_ID) } returns 브랜드_도메인_생성(
            id = 기본_브랜드_ID,
            deletedAtOrNull = ZonedDateTime.now(),
        )

        val ex = assertThrows<CoreException> {
            brandService.findById(기본_브랜드_ID)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
    }

    @Test
    fun `브랜드_ID_목록으로_활성_브랜드들을_조회한다`() {
        val brandRepository = mockk<BrandRepository>()
        val brandService = BrandService(brandRepository)
        every { brandRepository.findAllByIds(setOf(1L, 2L)) } returns listOf(
            브랜드_도메인_생성(id = 1L, name = "첫 브랜드"),
            브랜드_도메인_생성(id = 2L, name = "둘째 브랜드"),
        )

        val brands = brandService.findByIds(setOf(1L, 2L))

        assertThat(brands.map { it.id }).containsExactly(1L, 2L)
        assertThat(brands.map { it.name.value }).containsExactly("첫 브랜드", "둘째 브랜드")
    }

    @Test
    fun `브랜드_ID_목록에_존재하지_않는_ID가_있으면_NOT_FOUND가_발생한다`() {
        val brandRepository = mockk<BrandRepository>()
        val brandService = BrandService(brandRepository)
        every { brandRepository.findAllByIds(setOf(1L, 2L)) } returns listOf(
            브랜드_도메인_생성(id = 1L),
        )

        val ex = assertThrows<CoreException> {
            brandService.findByIds(setOf(1L, 2L))
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
    }
}
