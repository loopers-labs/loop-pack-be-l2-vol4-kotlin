package com.loopers.projection.like.infrastructure.persistence

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(
    basePackageClasses = [
        ProcessedKafkaEventJpaRepository::class,
        ProductLikeCountJpaRepository::class,
    ],
)
class LikeCountProjectionJpaConfig
