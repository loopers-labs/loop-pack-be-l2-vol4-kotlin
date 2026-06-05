package com.loopers.domain.product.unit

import com.loopers.domain.product.exception.InvalidProductException
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.ProductName
import com.loopers.domain.product.vo.Quantity
import com.loopers.domain.product.vo.StockQuantity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductVoValidationTest {
    @Test
    fun `상품명은_빈_문자열이면_생성이_불가하다`() {
        assertThrows<InvalidProductException> { ProductName.of("") }
    }

    @Test
    fun `상품명은_공백만_있으면_생성이_불가하다`() {
        assertThrows<InvalidProductException> { ProductName.of("   ") }
    }

    @Test
    fun `상품명은_공백이_아닌_문자가_있으면_생성된다`() {
        assertThat(ProductName.of("상품 A").value).isEqualTo("상품 A")
    }

    @Test
    fun `가격은_0원과_양수면_생성된다`() {
        assertThat(Money.of(0).value).isEqualTo(0)
        assertThat(Money.of(1).value).isEqualTo(1)
    }

    @Test
    fun `가격은_음수면_생성이_불가하다`() {
        assertThrows<InvalidProductException> { Money.of(-1) }
    }

    @Test
    fun `수량은_1개_이상이면_생성된다`() {
        assertThat(Quantity.of(1).value).isEqualTo(1)
    }

    @Test
    fun `수량은_0개_이하면_생성이_불가하다`() {
        assertThrows<InvalidProductException> { Quantity.of(0) }
        assertThrows<InvalidProductException> { Quantity.of(-1) }
    }

    @Test
    fun `재고는_0개와_양수면_생성된다`() {
        assertThat(StockQuantity.of(0).value).isEqualTo(0)
        assertThat(StockQuantity.of(1).value).isEqualTo(1)
    }

    @Test
    fun `재고는_음수면_생성이_불가하다`() {
        assertThrows<InvalidProductException> { StockQuantity.of(-1) }
    }
}
