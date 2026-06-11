package com.loopers.account.persistence

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackageClasses = [AccountJpaRepository::class])
class AccountPersistenceConfig
