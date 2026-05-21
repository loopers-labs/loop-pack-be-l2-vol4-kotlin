package com.loopers

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication
class AccountApiApplication

fun main(args: Array<String>) {
    runApplication<AccountApiApplication>(*args)
}
