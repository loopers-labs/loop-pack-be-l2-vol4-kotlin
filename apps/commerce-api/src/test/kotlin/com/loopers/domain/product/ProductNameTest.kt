package com.loopers.domain.product

import com.loopers.support.error.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class ProductNameTest {
    @DisplayName("1자 이름이 주어지면, 상품 이름 VO를 생성한다.")
    @Test
    fun createsProductName_whenValueHasMinLength() {
        val productName = ProductName("A")
        assertThat(productName.value).isEqualTo("A")
    }

    @DisplayName("100자 이름이 주어지면, 상품 이름 VO를 생성한다.")
    @Test
    fun createsProductName_whenValueHasMaxLength() {
        val value = "가".repeat(100)
        val productName = ProductName(value)
        assertThat(productName.value).isEqualTo(value)
    }

    @DisplayName("빈 문자열이 주어지면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenValueIsEmpty() {
        val result = assertThrows<BadRequestException> { ProductName("") }
        assertThat(result.errorCode).isEqualTo(ProductErrorCode.INVALID_PRODUCT_NAME)
    }

    @DisplayName("공백만으로 이루어진 이름이 주어지면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenValueIsBlank() {
        val result = assertThrows<BadRequestException> { ProductName("   ") }
        assertThat(result.errorCode).isEqualTo(ProductErrorCode.INVALID_PRODUCT_NAME)
    }

    @DisplayName("100자를 초과하는 이름이 주어지면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequestException_whenValueExceedsMaxLength() {
        val result = assertThrows<BadRequestException> { ProductName("가".repeat(101)) }
        assertThat(result.errorCode).isEqualTo(ProductErrorCode.INVALID_PRODUCT_NAME)
    }

    @DisplayName("같은 값의 상품 이름 VO는 동등하고, toString은 원문을 반환한다.")
    @Test
    fun equalsAndToString_followValueSemantics() {
        val first = ProductName("에어맥스")
        val second = ProductName("에어맥스")
        assertAll(
            { assertThat(first).isEqualTo(second) },
            { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
            { assertThat(first.toString()).isEqualTo("에어맥스") },
        )
    }
}
