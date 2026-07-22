package com.loopers.batch.listener

import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.AfterChunk
import org.springframework.batch.core.annotation.BeforeChunk
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.stereotype.Component

@Component
class ChunkListener {
    private val log = LoggerFactory.getLogger(ChunkListener::class.java)

    @BeforeChunk
    fun beforeChunk(chunkContext: ChunkContext) {
        chunkContext.setAttribute(START_AT_KEY, System.currentTimeMillis())
    }

    @AfterChunk
    fun afterChunk(chunkContext: ChunkContext) {
        val elapsedMs = (chunkContext.getAttribute(START_AT_KEY) as? Long)
            ?.let { System.currentTimeMillis() - it }
        log.info(
            "청크 종료: readCount: ${chunkContext.stepContext.stepExecution.readCount}, " +
                    "writeCount: ${chunkContext.stepContext.stepExecution.writeCount}, " +
                    "elapsedMs: ${elapsedMs ?: "-"}",
        )
    }

    companion object {
        private const val START_AT_KEY = "chunkStartAt"
    }
}
