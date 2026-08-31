package com.loopers.infrastructure.order
import com.loopers.domain.order.*
import org.springframework.stereotype.Component
@Component class OrderRepositoryImpl(private val jpa:OrderJpaRepository):OrderRepository{override fun find(id:Long)=jpa.findById(id).orElse(null);override fun save(order:Order)=jpa.save(order)}
