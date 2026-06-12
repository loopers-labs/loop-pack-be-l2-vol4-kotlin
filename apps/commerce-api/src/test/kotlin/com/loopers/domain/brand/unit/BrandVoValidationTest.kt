package com.loopers.domain.brand.unit

import com.loopers.domain.brand.exception.InvalidBrandException
import com.loopers.domain.brand.vo.BrandName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BrandVoValidationTest {
    @Test
    fun `브랜드명은_공백이면_생성이_불가하다`() {
        assertThrows<InvalidBrandException> { BrandName.of("") }
        assertThrows<InvalidBrandException> { BrandName.of("   ") }
    }

    @Test
    fun `브랜드명은_공백이_아닌_문자가_있으면_생성된다`() {
        assertThat(BrandName.of("브랜드").value).isEqualTo("브랜드")
    }
}
