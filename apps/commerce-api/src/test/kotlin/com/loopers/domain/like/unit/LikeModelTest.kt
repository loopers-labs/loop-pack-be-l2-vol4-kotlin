package com.loopers.domain.like.unit

import com.loopers.domain.like.exception.InvalidLikeException
import com.loopers.domain.like.support.LikeSteps.Companion.좋아요_도메인_생성
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LikeModelTest {
    @Test
    fun `사용자와_상품_ID가_유효하면_좋아요가_생성된다`() {
        val like = 좋아요_도메인_생성(userId = 1L, productId = 2L)

        assertThat(like.userId).isEqualTo(1L)
        assertThat(like.productId).isEqualTo(2L)
    }

    @Test
    fun `사용자와_상품_ID는_양수여야_한다`() {
        assertThrows<InvalidLikeException> { 좋아요_도메인_생성(userId = 0L) }
        assertThrows<InvalidLikeException> { 좋아요_도메인_생성(userId = -1L) }
        assertThrows<InvalidLikeException> { 좋아요_도메인_생성(userId = -100L) }
        assertThrows<InvalidLikeException> { 좋아요_도메인_생성(productId = 0L) }
        assertThrows<InvalidLikeException> { 좋아요_도메인_생성(productId = -1L) }
        assertThrows<InvalidLikeException> { 좋아요_도메인_생성(productId = -100L) }
    }
}
