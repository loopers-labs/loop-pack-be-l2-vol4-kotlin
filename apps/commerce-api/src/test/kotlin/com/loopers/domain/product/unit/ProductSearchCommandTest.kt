package com.loopers.domain.product.unit

import com.loopers.domain.product.application.command.ProductSearchCommand
import com.loopers.domain.product.vo.ProductSort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductSearchCommandTest {
    @Test
    fun `상품_검색조건은_기본값으로_최신순_첫페이지_20개를_사용한다`() {
        val command = ProductSearchCommand.of()

        assertThat(command.sort).isEqualTo(ProductSort.LATEST)
        assertThat(command.page).isEqualTo(0)
        assertThat(command.size).isEqualTo(20)
    }

    @Test
    fun `상품_검색조건은_가격_낮은순을_허용한다`() {
        val command = ProductSearchCommand.of(sort = "price_asc")

        assertThat(command.sort).isEqualTo(ProductSort.PRICE_ASC)
    }

    @Test
    fun `지원하지_않는_정렬조건은_BAD_REQUEST가_발생한다`() {
        val ex = assertThrows<CoreException> {
            ProductSearchCommand.of(sort = "unknown")
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @Test
    fun `페이지_번호는_0_이상이어야_한다`() {
        val ex = assertThrows<CoreException> {
            ProductSearchCommand.of(page = -1)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @Test
    fun `페이지_크기는_1_이상이어야_한다`() {
        val ex = assertThrows<CoreException> {
            ProductSearchCommand.of(size = 0)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
