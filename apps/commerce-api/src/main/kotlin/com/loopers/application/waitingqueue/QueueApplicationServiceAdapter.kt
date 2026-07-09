package com.loopers.application.waitingqueue

import com.loopers.domain.waitingqueue.AccessTokenIssueService
import com.loopers.domain.waitingqueue.QueueEntryService
import com.loopers.domain.waitingqueue.QueuePositionService
import com.loopers.domain.waitingqueue.model.QueueTopic
import com.loopers.interfaces.api.waitingqueue.QueueApplicationServicePort
import org.springframework.stereotype.Service

@Service
class QueueApplicationServiceAdapter(
    private val queueEntryService: QueueEntryService,
    private val queuePositionService: QueuePositionService,
    private val accessTokenIssueService: AccessTokenIssueService,
) : QueueApplicationServicePort {
    override fun enter(command: EnterCommand): WaitTokenResult {
        val token = queueEntryService.enter(
            topic = QueueTopic(command.topic),
            userId = command.userId,
            now = System.currentTimeMillis(),
        )
        return WaitTokenResult.from(token)
    }

    override fun position(query: PositionQuery): QueuePositionResult {
        val position = queuePositionService.position(query.rawWaitToken)
        return QueuePositionResult.from(position)
    }

    override fun issueAccessToken(command: IssueTokenCommand): AccessTokenResult {
        val accessToken = accessTokenIssueService.issue(command.rawWaitToken, System.currentTimeMillis())
        return AccessTokenResult.from(accessToken)
    }
}
