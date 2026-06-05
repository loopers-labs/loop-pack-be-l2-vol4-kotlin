package com.loopers.domain.product.unit

import com.loopers.domain.product.exception.InsufficientStockException
import com.loopers.domain.product.support.ProductSteps.Companion.재고_도메인_생성
import com.loopers.domain.product.vo.Quantity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StockModelTest {
    @Test
    fun `재고가_충분하면_차감된다`() {
        val stock = 재고_도메인_생성(leftStock = 10)

        val decreased = stock.decrease(Quantity.of(3))

        assertThat(decreased.leftStock.value).isEqualTo(7)
        assertThat(stock.leftStock.value).isEqualTo(10)
    }

    @Test
    fun `재고가_요청_수량과_같으면_0까지_차감된다`() {
        val stock = 재고_도메인_생성(leftStock = 10)

        val decreased = stock.decrease(Quantity.of(10))

        assertThat(decreased.leftStock.value).isEqualTo(0)
    }

    @Test
    fun `재고가_부족하면_차감이_불가하다`() {
        val stock = 재고_도메인_생성(leftStock = 2)

        assertThrows<InsufficientStockException> {
            stock.decrease(Quantity.of(3))
        }
        assertThat(stock.leftStock.value).isEqualTo(2)
    }

    @Test
    fun `가용_재고가_요청_수량_이상이면_충분하다`() {
        val stock = 재고_도메인_생성(leftStock = 2)

        assertThat(stock.hasEnough(Quantity.of(2))).isTrue()
        assertThat(stock.hasEnough(Quantity.of(3))).isFalse()
    }
}
