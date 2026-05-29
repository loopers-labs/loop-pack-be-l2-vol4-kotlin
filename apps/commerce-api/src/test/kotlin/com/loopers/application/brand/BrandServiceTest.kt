package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandErrorCode
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.shared.CursorPage
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrandServiceTest {
    private val brandRepository: BrandRepository = mock()
    private val brandService = BrandService(brandRepository)

    @DisplayName("유효한 이름으로 등록하면, 브랜드를 저장하고 정보를 반환한다.")
    @Test
    fun savesBrand_whenRegisterRequestIsValid() {
        whenever(brandRepository.existsByName(any())).thenReturn(false)
        whenever(brandRepository.save(any())).thenAnswer { it.arguments[0] as Brand }

        val info = brandService.register(BrandCreateCommand("나이키"))

        val captor = argumentCaptor<Brand>()
        verify(brandRepository).save(captor.capture())
        assertAll(
            { assertThat(captor.firstValue.name.value).isEqualTo("나이키") },
            { assertThat(info.name).isEqualTo("나이키") },
        )
    }

    @DisplayName("이미 존재하는 이름으로 등록하면, CONFLICT 예외가 발생하고 저장하지 않는다.")
    @Test
    fun throwsConflict_whenRegisterNameIsDuplicated() {
        whenever(brandRepository.existsByName(any())).thenReturn(true)

        val result = assertThrows<ConflictException> {
            brandService.register(BrandCreateCommand("나이키"))
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(BrandErrorCode.DUPLICATE_BRAND_NAME) },
            { verify(brandRepository, Mockito.never()).save(any()) },
        )
    }

    @DisplayName("존재하는 브랜드를 조회하면, 브랜드 정보를 반환한다.")
    @Test
    fun returnsBrandInfo_whenBrandExists() {
        whenever(brandRepository.findActiveById(1L)).thenReturn(Brand(BrandName("나이키")))

        val info = brandService.get(1L)

        assertThat(info.name).isEqualTo("나이키")
    }

    @DisplayName("존재하지 않는 브랜드를 조회하면, NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenBrandDoesNotExist() {
        whenever(brandRepository.findActiveById(1L)).thenReturn(null)

        val result = assertThrows<NotFoundException> {
            brandService.get(1L)
        }

        assertThat(result.errorCode).isEqualTo(BrandErrorCode.BRAND_NOT_FOUND)
    }

    @DisplayName("브랜드 목록을 조회하면, 페이지를 BrandInfo로 매핑해 반환한다.")
    @Test
    fun mapsBrandPageToInfo_whenListed() {
        whenever(brandRepository.findAll(null, 2)).thenReturn(
            CursorPage(
                content = listOf(Brand(BrandName("나이키")), Brand(BrandName("아디다스"))),
                hasNext = true,
            ),
        )

        val page = brandService.list(null, 2)

        assertAll(
            { assertThat(page.content.map { it.name }).containsExactly("나이키", "아디다스") },
            { assertThat(page.hasNext).isTrue() },
        )
    }

    @DisplayName("충돌 없이 이름을 수정하면, 이름이 변경된 정보를 반환한다.")
    @Test
    fun updatesName_whenNoConflict() {
        whenever(brandRepository.findActiveById(1L)).thenReturn(Brand(BrandName("나이키")))
        whenever(brandRepository.existsByNameExcludingId(any(), any())).thenReturn(false)

        val info = brandService.update(BrandUpdateCommand(1L, "아디다스"))

        assertThat(info.name).isEqualTo("아디다스")
    }

    @DisplayName("다른 브랜드와 충돌하는 이름으로 수정하면, CONFLICT 예외가 발생한다.")
    @Test
    fun throwsConflict_whenUpdateNameIsDuplicated() {
        whenever(brandRepository.findActiveById(1L)).thenReturn(Brand(BrandName("나이키")))
        whenever(brandRepository.existsByNameExcludingId(any(), any())).thenReturn(true)

        val result = assertThrows<ConflictException> {
            brandService.update(BrandUpdateCommand(1L, "아디다스"))
        }

        assertThat(result.errorCode).isEqualTo(BrandErrorCode.DUPLICATE_BRAND_NAME)
    }

    @DisplayName("존재하지 않는 브랜드를 수정하면, NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenUpdateTargetDoesNotExist() {
        whenever(brandRepository.findActiveById(1L)).thenReturn(null)

        val result = assertThrows<NotFoundException> {
            brandService.update(BrandUpdateCommand(1L, "아디다스"))
        }

        assertThat(result.errorCode).isEqualTo(BrandErrorCode.BRAND_NOT_FOUND)
    }
}
