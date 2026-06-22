package com.loopers.config.jpa

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * JDBC 기본 레이어. @Primary DataSource 로부터 [JdbcTemplate] 을 제공한다.
 * bulk insert([com.loopers.support.jdbc.JdbcBulkInserter]) 뿐 아니라
 * 다른 raw JDBC 작업에서도 이 기본 빈을 재사용한다.
 */
@Configuration
class JdbcConfig {
    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)
}
