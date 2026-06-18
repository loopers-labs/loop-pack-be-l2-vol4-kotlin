package com.loopers.domain.example

interface ExampleRepository {
    fun findByIdOrNull(id: Long): ExampleModel?
}
