package com.loopers.application.queue.usecase

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class EnterQueueUsecase(
    private val userService: UserService,
    private val queueRepository: OrderQueueRepository,
) {
    fun execute(loginId: String, password: String): Long {
        val user = userService.getProfile(loginId = loginId, password = password)
        return queueRepository.enter(userId = user.id, nowMillis = Instant.now().toEpochMilli())
    }
}
