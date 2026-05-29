package com.loopers.application.brand

import com.loopers.application.product.ProductService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class BrandFacadeTest {
    private val brandService: BrandService = mock()
    private val productService: ProductService = mock()
    private val brandFacade = BrandFacade(brandService, productService)

    @DisplayName("브랜드를 삭제하면, 브랜드 삭제 후 해당 브랜드 상품을 cascade soft delete 한다.")
    @Test
    fun deletesBrandThenCascadesToProducts() {
        brandFacade.delete(1L)

        inOrder(brandService, productService) {
            verify(brandService).delete(1L)
            verify(productService).softDeleteByBrand(1L)
        }
    }
}
