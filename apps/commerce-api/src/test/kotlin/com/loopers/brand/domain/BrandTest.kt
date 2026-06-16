package com.loopers.brand.domain

import com.loopers.support.error.ConflictException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class BrandTest {
    @DisplayName("브랜드를 생성하면, 기본 상태는 ACTIVE다.")
    @Test
    fun defaultsToActive_whenCreated() {
        val brand = Brand(BrandName("나이키"))

        assertThat(brand.status).isEqualTo(BrandStatus.ACTIVE)
    }

    @DisplayName("DELETED로 전이하면, status가 DELETED가 되고 deletedAt이 audit으로 기록된다.")
    @Test
    fun transitionsToDeleted() {
        val brand = Brand(BrandName("나이키"))

        brand.transitionTo(BrandStatus.DELETED)

        assertAll(
            { assertThat(brand.status).isEqualTo(BrandStatus.DELETED) },
            { assertThat(brand.deletedAt).isNotNull() },
        )
    }

    @DisplayName("같은 상태로의 전이는 멱등하다. (no-op)")
    @Test
    fun transitionIsIdempotent_forSameStatus() {
        val brand = Brand(BrandName("나이키"))

        brand.transitionTo(BrandStatus.DELETED)
        val firstDeletedAt = brand.deletedAt
        brand.transitionTo(BrandStatus.DELETED)

        assertAll(
            { assertThat(brand.status).isEqualTo(BrandStatus.DELETED) },
            { assertThat(brand.deletedAt).isEqualTo(firstDeletedAt) },
        )
    }

    @DisplayName("허용되지 않은 상태 전이(DELETED→ACTIVE)는 CONFLICT 예외가 발생한다.")
    @Test
    fun throwsConflict_whenTransitionIsNotAllowed() {
        val brand = Brand(BrandName("나이키"))
        brand.transitionTo(BrandStatus.DELETED)

        val result = assertThrows<ConflictException> {
            brand.transitionTo(BrandStatus.ACTIVE)
        }

        assertThat(result.errorCode).isEqualTo(BrandErrorCode.INVALID_BRAND_STATUS_TRANSITION)
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
