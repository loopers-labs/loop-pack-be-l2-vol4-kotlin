package com.loopers.domain.brand

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class BrandTest {
    @DisplayName("브랜드를 삭제하면, deletedAt이 설정된다.")
    @Test
    fun setsDeletedAt_whenDeleted() {
        val brand = Brand(BrandName("나이키"))

        brand.delete()

        assertThat(brand.deletedAt).isNotNull()
    }

    @DisplayName("브랜드를 두 번 삭제해도, deletedAt은 최초 삭제 시점을 유지한다. (멱등)")
    @Test
    fun keepsDeletedAt_whenDeletedTwice() {
        val brand = Brand(BrandName("나이키"))

        brand.delete()
        val firstDeletedAt = brand.deletedAt
        brand.delete()

        assertThat(brand.deletedAt).isEqualTo(firstDeletedAt)
    }

    @DisplayName("브랜드를 수정하면, name과 description이 변경된다.")
    @Test
    fun changesNameAndDescription_whenUpdated() {
        val brand = Brand(BrandName("나이키"), "스포츠 브랜드")

        brand.update(BrandName("아디다스"), "독일 스포츠 브랜드")

        assertAll(
            { assertThat(brand.name).isEqualTo(BrandName("아디다스")) },
            { assertThat(brand.description).isEqualTo("독일 스포츠 브랜드") },
        )
    }
}
