package com.loopers.domain.waitingqueue.unit

import com.loopers.domain.waitingqueue.config.WaitingQueueProperties
import com.loopers.domain.waitingqueue.config.WaitingQueueRedisKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.FileSystemResource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class WaitingQueuePropertiesTest {
    @Test
    fun `기본_application_yml의_입장_batch_size는_벤치마크_기준_100이다`() {
        val properties = bindDefaultProperties()

        assertThat(properties.admissionBatchSize).isEqualTo(100)
    }

    @Test
    fun `기본_application_yml의_scheduler_jitter_max는_즉시_입장을_위해_0초다`() {
        val properties = bindDefaultProperties()

        assertThat(properties.schedulerJitterMax).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `Redis_key_이름과_토큰_prefix는_application_yml에서_바인딩된다`() {
        val properties = bindDefaultProperties()

        assertThat(properties.tokenPrefix).isEqualTo("q_")
        assertThat(properties.redisKeys.entries).isEqualTo("entries")
        assertThat(properties.redisKeys.sequence).isEqualTo("sequence")
        assertThat(properties.redisKeys.userAdmissionPrefix).isEqualTo("admission:user")
        assertThat(properties.redisKeys.tokenAdmissionPrefix).isEqualTo("admission:token")
    }

    @Test
    fun `Redis_millisecond_해상도보다_짧은_token_TTL은_거부한다`() {
        assertThrows<IllegalArgumentException> {
            properties(tokenTtl = Duration.ofNanos(500_000))
        }
    }

    @Test
    fun `정적_Redis_key와_동적_admission_prefix_하위가_겹치면_거부한다`() {
        assertThrows<IllegalArgumentException> {
            WaitingQueueRedisKeys(
                entries = "admission:user:1",
                sequence = "sequence",
                userAdmissionPrefix = "admission:user",
                tokenAdmissionPrefix = "admission:token",
            )
        }
    }

    private fun bindDefaultProperties(): WaitingQueueProperties {
        val propertySource = YamlPropertySourceLoader()
            .load("application", FileSystemResource(resolveApplicationYaml()))
            .first { it.getProperty("spring.config.activate.on-profile") == null }
        return Binder(ConfigurationPropertySources.from(propertySource))
            .bind("commerce.waiting-queue", WaitingQueueProperties::class.java)
            .get()
    }

    private fun resolveApplicationYaml(): Path {
        val workingDirectory = Path.of("").toAbsolutePath()
        return listOf(
            workingDirectory.resolve("src/main/resources/application.yml"),
            workingDirectory.resolve("apps/commerce-api/src/main/resources/application.yml"),
        ).first(Files::exists)
    }

    private fun properties(tokenTtl: Duration): WaitingQueueProperties = WaitingQueueProperties(
        schedulerEnabled = true,
        tokenTtl = tokenTtl,
        schedulerDelay = Duration.ofSeconds(10),
        admissionBatchSize = 100,
        schedulerJitterMax = Duration.ZERO,
        pollingInterval = Duration.ofSeconds(2),
        tokenPrefix = "q_",
        redisKeyPrefix = "waiting-queue",
        redisKeys = WaitingQueueRedisKeys(
            entries = "entries",
            sequence = "sequence",
            userAdmissionPrefix = "admission:user",
            tokenAdmissionPrefix = "admission:token",
        ),
    )
}
