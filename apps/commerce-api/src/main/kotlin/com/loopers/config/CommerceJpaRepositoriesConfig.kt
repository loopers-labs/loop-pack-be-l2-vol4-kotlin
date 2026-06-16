package com.loopers.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * commerce-api 의 도메인-우선 패키지(com.loopers.<context>.infrastructure)에 있는
 * Spring Data JPA 리포지토리를 스캔한다.
 *
 * 베이스 템플릿인 modules/jpa 의 [com.loopers.config.jpa.JpaConfig] 는 수정하지 않는다 —
 * 해당 설정의 com.loopers.infrastructure 스캔은 이 앱에서 빈 패키지라 무해하다.
 */
@Configuration
@EnableJpaRepositories(basePackages = ["com.loopers"])
class CommerceJpaRepositoriesConfig
