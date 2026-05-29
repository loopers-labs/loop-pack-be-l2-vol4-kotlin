package com.loopers.support

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class DatabaseCleanupIntegrationTest @Autowired constructor(
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @Test
    @DisplayName("test 프로파일에서 DatabaseCleanup 빈이 주입되고 execute()가 예외 없이 동작한다")
    fun executesWithoutError() {
        assertThatCode { databaseCleanup.execute() }.doesNotThrowAnyException()
    }
}
