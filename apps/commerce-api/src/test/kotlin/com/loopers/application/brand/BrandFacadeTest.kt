package com.loopers.application.brand

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class BrandFacadeTest {
    private val brandService: BrandService = mock()
    private val brandFacade = BrandFacade(brandService)

    @DisplayName("브랜드 삭제를 BrandService에 위임한다. (Product cascade는 Product 머지 후 추가)")
    @Test
    fun delegatesDeleteToBrandService() {
        brandFacade.delete(1L)

        verify(brandService).delete(1L)
    }
}
