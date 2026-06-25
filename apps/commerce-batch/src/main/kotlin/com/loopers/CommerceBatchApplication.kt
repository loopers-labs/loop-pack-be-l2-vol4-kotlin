package com.loopers

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import java.util.TimeZone
import kotlin.system.exitProcess

@EnableFeignClients(basePackages = ["com.loopers.infrastructure.payment"])
@ConfigurationPropertiesScan
@SpringBootApplication
@ComponentScan(
    basePackages = ["com.loopers"],
    excludeFilters = [
        // commerce-api 웹 계층은 batch 에서 로드하지 않는다(비웹 컨텍스트).
        ComponentScan.Filter(type = FilterType.REGEX, pattern = ["com\\.loopers\\.interfaces\\..*"]),
        // 다른 앱의 메인 클래스가 설정으로 재처리되지 않게 제외.
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [CommerceApiApplication::class]),
    ],
)
class CommerceBatchApplication

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    val exitCode = SpringApplication.exit(runApplication<CommerceBatchApplication>(*args))
    exitProcess(exitCode)
}
