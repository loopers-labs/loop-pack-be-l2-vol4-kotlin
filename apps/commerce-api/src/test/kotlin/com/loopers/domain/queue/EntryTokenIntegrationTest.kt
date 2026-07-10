package com.loopers.domain.queue

import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest
class EntryTokenIntegrationTest @Autowired constructor(
    private val entryTokenService: EntryTokenService,
    private val entryTokenRepository: EntryTokenRepository,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("토큰을 발급하면 활성 수에 반영되고, 올바른 토큰으로 소비하면 성공하며 다시 소비할 수 없다.")
    @Test
    fun issueThenConsume() {
        // arrange
        val userId = 1L
        val token = entryTokenService.issue(userId)

        // act & assert
        assertThat(entryTokenService.activeCount()).isEqualTo(1L)
        assertThat(entryTokenService.consume(userId, token)).isTrue() // 1회용 성공
        assertThat(entryTokenService.consume(userId, token)).isFalse() // 재사용 불가
        assertThat(entryTokenService.activeCount()).isEqualTo(0L) // 활성에서 제거됨
    }

    @DisplayName("틀린 토큰으로 소비를 시도하면 실패하고, 원래 토큰은 여전히 유효하다. (griefing 방지)")
    @Test
    fun consumeWithWrongToken() {
        // arrange
        val userId = 1L
        val token = entryTokenService.issue(userId)

        // act
        val wrong = entryTokenService.consume(userId, "wrong-token")

        // assert
        assertThat(wrong).isFalse()
        assertThat(entryTokenService.consume(userId, token)).isTrue() // 원래 토큰은 살아있음
    }

    @DisplayName("만료 시각이 지난 토큰은 활성 수에서 제외된다. (ZREMRANGEBYSCORE 정리)")
    @Test
    fun activeCountSweepsExpired() {
        // arrange : 이미 만료된 토큰과 유효한 토큰을 직접 저장
        entryTokenRepository.save(1L, "t1", Instant.parse("2020-01-01T00:00:00Z"))
        entryTokenRepository.save(2L, "t2", Instant.now().plusSeconds(300))

        // act
        val count = entryTokenRepository.activeCount(Instant.now())

        // assert
        assertThat(count).isEqualTo(1L)
    }
}
