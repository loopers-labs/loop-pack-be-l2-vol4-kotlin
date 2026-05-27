package com.loopers.application.like

import com.loopers.domain.like.LikeService
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component

@Component
class LikeFacade(
    private val likeService: LikeService,
    private val userService: UserService,
) {
    fun like(command: LikeCommand) {
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        likeService.like(userId = user.id, productId = command.productId)
    }

    fun unlike(command: LikeCommand) {
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        likeService.unlike(userId = user.id, productId = command.productId)
    }

    data class LikeCommand(
        val loginId: String,
        val password: String,
        val productId: Long,
    )
}
