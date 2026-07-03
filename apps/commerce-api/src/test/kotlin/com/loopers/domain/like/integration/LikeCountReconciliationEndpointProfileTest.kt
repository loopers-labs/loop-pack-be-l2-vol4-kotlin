package com.loopers.domain.like.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.profiles.active=test",
        "commerce-events.outbox-relay.enabled=false",
    ],
)
class LikeCountReconciliationEndpointProfileTest
    @Autowired
    constructor(
        private val testRestTemplate: TestRestTemplate,
    ) {
        @Test
        fun `비local_profile에서는_좋아요_수_재구성_endpoint가_노출되지_않는다`() {
            val response = testRestTemplate.exchange(
                "/api/v1/dev/rebuild-like-counts",
                HttpMethod.POST,
                null,
                String::class.java,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
