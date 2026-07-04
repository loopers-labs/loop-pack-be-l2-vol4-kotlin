package com.loopers.support.outbox.persistence

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackageClasses = [OutboxEventJpaRepository::class])
class OutboxJpaConfig
