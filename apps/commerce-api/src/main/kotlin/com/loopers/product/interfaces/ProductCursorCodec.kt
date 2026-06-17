package com.loopers.product.interfaces

import com.loopers.product.domain.LikeCountCursor
import com.loopers.product.domain.PriceCursor
import com.loopers.product.domain.ProductErrorCode
import com.loopers.product.domain.ProductSort
import com.loopers.shared.domain.Cursor
import com.loopers.shared.domain.IdCursor
import com.loopers.support.error.BadRequestException
import java.util.Base64

/**
 * 상품 목록 keyset 커서의 불투명(opaque) base64 인코딩/디코딩.
 *
 * 도메인은 타입 있는 [Cursor]만 다루고, 클라이언트에 노출하는 wire 포맷(base64 문자열)은
 * presentation(interfaces) 책임이다([com.loopers.shared.domain.CursorPage] 주석 참조).
 *
 * 토큰 평문 포맷: `"<sort>:<keys...>"` (정렬별 키 개수 다름) → URL-safe base64(no padding).
 * 디코딩 시 선두 정렬명이 요청 정렬과 다르거나 파싱이 깨지면 [ProductErrorCode.INVALID_PRODUCT_CURSOR] (400).
 */
object ProductCursorCodec {
    private const val DELIMITER = ":"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(sort: ProductSort, cursor: Cursor?): String? {
        if (cursor == null) {
            return null
        }
        val plain = when (cursor) {
            is IdCursor -> "${sort.name}$DELIMITER${cursor.id}"
            is PriceCursor -> "${sort.name}$DELIMITER${cursor.price}$DELIMITER${cursor.id}"
            is LikeCountCursor -> "${sort.name}$DELIMITER${cursor.likeCount}$DELIMITER${cursor.id}"
            else -> throw invalidCursor()
        }
        return encoder.encodeToString(plain.toByteArray(Charsets.UTF_8))
    }

    fun decode(sort: ProductSort, token: String?): Cursor? {
        if (token.isNullOrBlank()) {
            return null
        }
        val plain = try {
            String(decoder.decode(token), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw invalidCursor()
        }
        val parts = plain.split(DELIMITER)
        if (parts.firstOrNull() != sort.name) {
            throw invalidCursor()
        }
        return try {
            when (sort) {
                ProductSort.LATEST -> {
                    require(parts.size == 2)
                    IdCursor(parts[1].toLong())
                }
                ProductSort.PRICE_ASC -> {
                    require(parts.size == 3)
                    PriceCursor(parts[1].toLong(), parts[2].toLong())
                }
                ProductSort.LIKES_DESC -> {
                    require(parts.size == 3)
                    LikeCountCursor(parts[1].toLong(), parts[2].toLong())
                }
            }
        } catch (e: IllegalArgumentException) {
            throw invalidCursor()
        }
    }

    private fun invalidCursor(): BadRequestException =
        BadRequestException(ProductErrorCode.INVALID_PRODUCT_CURSOR)
}
