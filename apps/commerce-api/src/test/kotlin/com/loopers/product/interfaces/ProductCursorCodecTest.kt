package com.loopers.product.interfaces

import com.loopers.product.domain.LikeCountCursor
import com.loopers.product.domain.PriceCursor
import com.loopers.product.domain.ProductSort
import com.loopers.shared.domain.IdCursor
import com.loopers.support.error.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProductCursorCodecTest {
    @DisplayName("LATEST 커서를 인코딩 후 디코딩하면 원본과 같다.")
    @Test
    fun latestRoundTrip() {
        val cursor = IdCursor(42)
        val token = ProductCursorCodec.encode(ProductSort.LATEST, cursor)
        assertEquals(cursor, ProductCursorCodec.decode(ProductSort.LATEST, token))
    }

    @DisplayName("PRICE_ASC 커서를 인코딩 후 디코딩하면 원본과 같다.")
    @Test
    fun priceRoundTrip() {
        val cursor = PriceCursor(price = 12_900, id = 7)
        val token = ProductCursorCodec.encode(ProductSort.PRICE_ASC, cursor)
        assertEquals(cursor, ProductCursorCodec.decode(ProductSort.PRICE_ASC, token))
    }

    @DisplayName("LIKES_DESC 커서를 인코딩 후 디코딩하면 원본과 같다.")
    @Test
    fun likesRoundTrip() {
        val cursor = LikeCountCursor(likeCount = 55_000, id = 7)
        val token = ProductCursorCodec.encode(ProductSort.LIKES_DESC, cursor)
        assertEquals(cursor, ProductCursorCodec.decode(ProductSort.LIKES_DESC, token))
    }

    @DisplayName("요청 정렬과 다른 정렬로 만든 커서를 디코딩하면 400 예외다.")
    @Test
    fun throwsWhenSortMismatch() {
        val token = ProductCursorCodec.encode(ProductSort.LIKES_DESC, LikeCountCursor(10, 1))
        assertThrows(BadRequestException::class.java) {
            ProductCursorCodec.decode(ProductSort.PRICE_ASC, token)
        }
    }

    @DisplayName("base64 가 아닌 토큰을 디코딩하면 400 예외다.")
    @Test
    fun throwsWhenTokenIsNotBase64() {
        assertThrows(BadRequestException::class.java) {
            ProductCursorCodec.decode(ProductSort.LATEST, "!!!not-base64!!!")
        }
    }

    @DisplayName("null/빈 커서는 인코딩·디코딩 모두 null 이다.")
    @Test
    fun nullCursorIsNull() {
        assertNull(ProductCursorCodec.encode(ProductSort.LATEST, null))
        assertNull(ProductCursorCodec.decode(ProductSort.LATEST, null))
        assertNull(ProductCursorCodec.decode(ProductSort.LATEST, ""))
    }
}
