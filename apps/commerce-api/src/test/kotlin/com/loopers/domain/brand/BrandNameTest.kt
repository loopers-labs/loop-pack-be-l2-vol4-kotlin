package com.loopers.domain.brand

import com.loopers.support.error.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class BrandNameTest {
    @DisplayName("1자 이름이 주어지면, 브랜드 이름 VO를 생성한다.")
    @Test
    fun createsBrandName_whenValueHasMinLength() {
        val brandName = BrandName("A")
        assertThat(brandName.value).isEqualTo("A")
    }

    @DisplayName("50자 이름이 주어지면, 브랜드 이름 VO를 생성한다.")
    @Test
    fun createsBrandName_whenValueHasMaxLength() {
        val value = "가".repeat(50)
        val brandName = BrandName(value)
        assertThat(brandName.value).isEqualTo(value)
    }

    @DisplayName("빈 문자열이 주어지면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenValueIsEmpty() {
        val result = assertThrows<BadRequestException> { BrandName("") }
        assertThat(result.errorCode).isEqualTo(BrandErrorCode.INVALID_BRAND_NAME)
    }

    @DisplayName("공백만으로 이루어진 이름이 주어지면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenValueIsBlank() {
        val result = assertThrows<BadRequestException> { BrandName("   ") }
        assertThat(result.errorCode).isEqualTo(BrandErrorCode.INVALID_BRAND_NAME)
    }

    @DisplayName("51자 이름이 주어지면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenValueExceedsMaxLength() {
        val result = assertThrows<BadRequestException> { BrandName("가".repeat(51)) }
        assertThat(result.errorCode).isEqualTo(BrandErrorCode.INVALID_BRAND_NAME)
    }

    @DisplayName("같은 값의 브랜드 이름 VO는 동등하고, toString은 원문을 반환한다.")
    @Test
    fun equalsAndToString_followValueSemantics() {
        val first = BrandName("나이키")
        val second = BrandName("나이키")
        assertAll(
            { assertThat(first).isEqualTo(second) },
            { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
            { assertThat(first.toString()).isEqualTo("나이키") },
        )
    }
}
