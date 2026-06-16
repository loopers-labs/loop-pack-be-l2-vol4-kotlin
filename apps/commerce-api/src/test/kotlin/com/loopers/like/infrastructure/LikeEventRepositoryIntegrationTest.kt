package com.loopers.like.infrastructure

import com.loopers.like.domain.LikeAction
import com.loopers.like.domain.LikeEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class LikeEventRepositoryIntegrationTest @Autowired constructor(
    private val likeEventJpaRepository: LikeEventJpaRepository,
) {
    private val repository = LikeEventRepositoryImpl(likeEventJpaRepository)

    @DisplayName("같은 (userId, productId)의 LIKE·UNLIKE를 모두 append할 수 있다. (append-only, UK 없음)")
    @Test
    fun appendsMultipleEvents_forSameUserAndProduct() {
        repository.append(LikeEvent(10L, 100L, LikeAction.LIKE))
        repository.append(LikeEvent(10L, 100L, LikeAction.UNLIKE))

        assertThat(likeEventJpaRepository.findAll().map { it.action })
            .containsExactlyInAnyOrder(LikeAction.LIKE, LikeAction.UNLIKE)
    }
}
