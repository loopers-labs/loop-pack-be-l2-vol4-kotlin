package com.loopers.application.like

import com.loopers.domain.like.LikeRepositoryPort
import com.loopers.domain.like.LikeService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LikeConfig {
    @Bean
    fun likeService(likeRepositoryPort: LikeRepositoryPort): LikeService =
        LikeService(likeRepositoryPort)
}
