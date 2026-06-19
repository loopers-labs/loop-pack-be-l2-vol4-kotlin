package com.loopers.domain.shopping

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "carts",
    uniqueConstraints = [UniqueConstraint(name = "uk_carts_user_id", columnNames = ["user_id"])],
)
class Cart(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
) : BaseEntity()
