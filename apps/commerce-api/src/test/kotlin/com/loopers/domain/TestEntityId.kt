package com.loopers.domain

fun <T : BaseEntity> T.withId(id: Long): T {
    val field = BaseEntity::class.java.getDeclaredField("id")
    field.isAccessible = true
    field.set(this, id)
    return this
}
