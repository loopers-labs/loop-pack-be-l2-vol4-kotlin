package com.loopers.application.queue.usecase

import com.loopers.application.queue.QueuePosition
import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component

@Component
class GetQueuePositionUsecase(
    private val userService: UserService,
    private val queueRepository: OrderQueueRepository,
) {
    fun execute(loginId: String, password: String): QueuePosition {
        val user = userService.getProfile(loginId = loginId, password = password)
        val rank = queueRepository.rank(user.id)
        return if (rank != null) {
            QueuePosition(position = rank + 1, waiting = true)
        } else {
            QueuePosition(position = null, waiting = false)
        }
    }
}
