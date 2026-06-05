package com.loopers.domain.brand.unit

import com.loopers.domain.brand.exception.BrandNotActiveException
import com.loopers.domain.brand.support.BrandSteps.Companion.기본_브랜드명
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_도메인_생성
import com.loopers.domain.brand.vo.BrandName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BrandModelTest {
    @Test
    fun `모든_필드가_유효하면_브랜드가_생성된다`() {
        val brand = 브랜드_도메인_생성()

        assertThat(brand.name.value).isEqualTo(기본_브랜드명)
        assertThat(brand.deletedAtOrNull).isNull()
    }

    @Test
    fun `브랜드명을_변경할_수_있다`() {
        val brand = 브랜드_도메인_생성()

        val renamed = brand.rename(BrandName.of("변경 브랜드"))

        assertThat(renamed.name.value).isEqualTo("변경 브랜드")
        assertThat(brand.name.value).isEqualTo(기본_브랜드명)
    }

    @Test
    fun `삭제되지_않은_브랜드는_활성_상태다`() {
        val brand = 브랜드_도메인_생성()

        assertThatCode { brand.requireActive() }.doesNotThrowAnyException()
    }

    @Test
    fun `삭제된_브랜드는_활성_상태가_아니다`() {
        val brand = 브랜드_도메인_생성().delete()

        assertThrows<BrandNotActiveException> {
            brand.requireActive()
        }
    }

    @Test
    fun `브랜드_삭제는_멱등하다`() {
        val brand = 브랜드_도메인_생성()

        val deleted = brand.delete()
        val deletedAgain = deleted.delete()

        assertThat(deleted.deletedAtOrNull).isNotNull()
        assertThat(deletedAgain.deletedAtOrNull).isEqualTo(deleted.deletedAtOrNull)
    }
}
