package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class BrandTest {
    @DisplayName("브랜드 생성 시, ")
    @Nested
    inner class CreateBrand {
        @DisplayName("모든 값이 유효하면 정상적으로 생성된다.")
        @Test
        fun createBrand_whenAllFieldsAreValid() {
            // arrange
            val name = "Loopers"
            val description = "감성 이커머스 브랜드"
            val logoImageUrl = "https://example.com/logo.png"

            // act
            val brand = Brand(name = name, description = description, logoImageUrl = logoImageUrl)

            // assert
            assertAll(
                { assertThat(brand.name).isEqualTo(name) },
                { assertThat(brand.description).isEqualTo(description) },
                { assertThat(brand.logoImageUrl).isEqualTo(logoImageUrl) },
            )
        }

        @DisplayName("브랜드명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            // act & assert
            val result = assertThrows<CoreException> {
                Brand(name = " ", description = "감성 이커머스 브랜드")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("브랜드 설명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenDescriptionIsBlank() {
            // act & assert
            val result = assertThrows<CoreException> {
                Brand(name = "Loopers", description = " ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("브랜드 로고 이미지 URL이 공백이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLogoImageUrlIsBlank() {
            // act & assert
            val result = assertThrows<CoreException> {
                Brand(name = "Loopers", description = "감성 이커머스 브랜드", logoImageUrl = " ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("브랜드명 변경 시, ")
    @Nested
    inner class RenameBrand {
        @DisplayName("브랜드명이 유효하면 브랜드명이 변경된다.")
        @Test
        fun renameBrand_whenNameIsValid() {
            // arrange
            val brand = Brand(name = "Loopers", description = "감성 이커머스 브랜드")

            // act
            brand.rename(name = "New Loopers")

            // assert
            assertAll(
                { assertThat(brand.name).isEqualTo("New Loopers") },
                { assertThat(brand.description).isEqualTo("감성 이커머스 브랜드") },
            )
        }

        @DisplayName("브랜드명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            // arrange
            val brand = Brand(name = "Loopers", description = "감성 이커머스 브랜드")

            // act & assert
            val result = assertThrows<CoreException> {
                brand.rename(name = " ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("브랜드 로고 이미지 URL 변경 시, ")
    @Nested
    inner class ChangeLogoImageUrl {
        @DisplayName("브랜드 로고 이미지 URL이 유효하면 변경된다.")
        @Test
        fun changeLogoImageUrl_whenLogoImageUrlIsValid() {
            // arrange
            val brand = Brand(name = "Loopers", description = "감성 이커머스 브랜드")

            // act
            brand.changeLogoImageUrl(logoImageUrl = "https://example.com/new-logo.png")

            // assert
            assertThat(brand.logoImageUrl).isEqualTo("https://example.com/new-logo.png")
        }

        @DisplayName("브랜드 로고 이미지 URL은 제거할 수 있다.")
        @Test
        fun changeLogoImageUrl_whenLogoImageUrlIsNull() {
            // arrange
            val brand = Brand(
                name = "Loopers",
                description = "감성 이커머스 브랜드",
                logoImageUrl = "https://example.com/logo.png",
            )

            // act
            brand.changeLogoImageUrl(logoImageUrl = null)

            // assert
            assertThat(brand.logoImageUrl).isNull()
        }

        @DisplayName("브랜드 로고 이미지 URL이 공백이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLogoImageUrlIsBlank() {
            // arrange
            val brand = Brand(name = "Loopers", description = "감성 이커머스 브랜드")

            // act & assert
            val result = assertThrows<CoreException> {
                brand.changeLogoImageUrl(logoImageUrl = " ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("브랜드 설명 변경 시, ")
    @Nested
    inner class ChangeDescription {
        @DisplayName("브랜드 설명이 유효하면 브랜드 설명이 변경된다.")
        @Test
        fun changeDescription_whenDescriptionIsValid() {
            // arrange
            val brand = Brand(name = "Loopers", description = "감성 이커머스 브랜드")

            // act
            brand.changeDescription(description = "새로운 브랜드 설명")

            // assert
            assertAll(
                { assertThat(brand.name).isEqualTo("Loopers") },
                { assertThat(brand.description).isEqualTo("새로운 브랜드 설명") },
            )
        }

        @DisplayName("브랜드 설명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenDescriptionIsBlank() {
            // arrange
            val brand = Brand(name = "Loopers", description = "감성 이커머스 브랜드")

            // act & assert
            val result = assertThrows<CoreException> {
                brand.changeDescription(description = " ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
