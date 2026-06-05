package com.loopers.domain.product.unit

import com.loopers.domain.product.exception.ProductNotOrderableException
import com.loopers.domain.product.support.ProductSteps.Companion.기본_가격
import com.loopers.domain.product.support.ProductSteps.Companion.기본_브랜드_ID
import com.loopers.domain.product.support.ProductSteps.Companion.기본_상품명
import com.loopers.domain.product.support.ProductSteps.Companion.상품_도메인_생성
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.ProductName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductModelTest {
    @Test
    fun `모든_필드가_유효하면_상품이_생성된다`() {
        val product = 상품_도메인_생성()

        assertThat(product.brandId).isEqualTo(기본_브랜드_ID)
        assertThat(product.name.value).isEqualTo(기본_상품명)
        assertThat(product.price.value).isEqualTo(기본_가격)
        assertThat(product.deletedAtOrNull).isNull()
    }

    @Test
    fun `상품명과_가격을_변경할_수_있다`() {
        val product = 상품_도메인_생성()

        val changed = product
            .changeName(ProductName.of("변경 상품"))
            .changePrice(Money.of(20_000))

        assertThat(changed.name.value).isEqualTo("변경 상품")
        assertThat(changed.price.value).isEqualTo(20_000)
        assertThat(product.name.value).isEqualTo(기본_상품명)
        assertThat(product.price.value).isEqualTo(기본_가격)
    }

    @Test
    fun `삭제되지_않은_상품은_주문_가능하다`() {
        val product = 상품_도메인_생성()

        assertThatCode { product.requireOrderable() }.doesNotThrowAnyException()
    }

    @Test
    fun `삭제된_상품은_주문_불가하다`() {
        val product = 상품_도메인_생성().delete()

        assertThrows<ProductNotOrderableException> {
            product.requireOrderable()
        }
    }

    @Test
    fun `상품_삭제는_멱등하다`() {
        val product = 상품_도메인_생성()

        val deleted = product.delete()
        val deletedAgain = deleted.delete()

        assertThat(deleted.deletedAtOrNull).isNotNull()
        assertThat(deletedAgain.deletedAtOrNull).isEqualTo(deleted.deletedAtOrNull)
    }
}
