package com.loopers.domain.order
interface OrderRepository { fun find(id:Long):Order?; fun save(order:Order):Order }
