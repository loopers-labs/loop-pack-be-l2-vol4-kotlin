package com.loopers.application.outbox

import com.loopers.domain.outbox.Outbox

data class OutboxSavedEvent(val outbox: Outbox)
