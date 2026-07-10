package com.loopers.domain.waitingqueue.infrastructure.redis.constant

object WaitingQueueRedisScripts {
    const val ENQUEUE = """
        local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
        if score then
            return tonumber(score)
        end
        if redis.call('EXISTS', KEYS[3]) == 1 then
            return 0
        end
        local sequence = redis.call('INCR', KEYS[2])
        redis.call('ZADD', KEYS[1], 'NX', sequence, ARGV[1])
        return sequence
    """

    const val FIND_STATE = """
        local token = redis.call('HGET', KEYS[2], '${WaitingQueueRedisConstants.TOKEN_FIELD}')
        if token then
            local ttl = redis.call('PTTL', KEYS[2])
            if ttl > 0 then
                return {
                    '${WaitingQueueRedisConstants.ADMITTED_QUEUE_STATUS}',
                    token,
                    redis.call('HGET', KEYS[2], '${WaitingQueueRedisConstants.AVAILABLE_AT_FIELD}'),
                    tostring(ttl),
                    redis.call('HGET', KEYS[2], '${WaitingQueueRedisConstants.SEQUENCE_FIELD}'),
                    tostring(redis.call('ZCARD', KEYS[1]))
                }
            end
        end
        local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
        if rank then
            return {
                '${WaitingQueueRedisConstants.WAITING_QUEUE_STATUS}',
                redis.call('ZSCORE', KEYS[1], ARGV[1]),
                tostring(rank),
                tostring(redis.call('ZCARD', KEYS[1]))
            }
        end
        return {}
    """

    const val ADMIT = """
        local requestedCount = tonumber(ARGV[1])
        local userKeyPrefix = ARGV[2]
        local tokenKeyPrefix = ARGV[3]
        local ttl = tonumber(ARGV[4])
        local plannedCount = math.min(requestedCount, redis.call('ZCARD', KEYS[1]))
        local candidateIndex = 5
        local seenTokens = {}
        for index = 1, plannedCount do
            local token = ARGV[candidateIndex]
            local tokenKey = tokenKeyPrefix .. ':' .. token
            if seenTokens[token] or redis.call('EXISTS', tokenKey) == 1 then
                return {}
            end
            seenTokens[token] = true
            candidateIndex = candidateIndex + 3
        end
        local popped = redis.call('ZPOPMIN', KEYS[1], requestedCount)
        local result = {}
        candidateIndex = 5
        for index = 1, #popped, 2 do
            local userId = popped[index]
            local sequence = popped[index + 1]
            local token = ARGV[candidateIndex]
            local availableAt = ARGV[candidateIndex + 1]
            local expiresAt = ARGV[candidateIndex + 2]
            candidateIndex = candidateIndex + 3
            local userKey = userKeyPrefix .. ':' .. userId
            local tokenKey = tokenKeyPrefix .. ':' .. token
            if redis.call('EXISTS', userKey) == 0 then
                redis.call(
                    'HSET', userKey,
                    '${WaitingQueueRedisConstants.STATUS_FIELD}', '${WaitingQueueRedisConstants.ACTIVE_STATUS}',
                    '${WaitingQueueRedisConstants.TOKEN_FIELD}', token,
                    '${WaitingQueueRedisConstants.USER_ID_FIELD}', userId,
                    '${WaitingQueueRedisConstants.SEQUENCE_FIELD}', sequence,
                    '${WaitingQueueRedisConstants.AVAILABLE_AT_FIELD}', availableAt,
                    '${WaitingQueueRedisConstants.EXPIRES_AT_FIELD}', expiresAt
                )
                redis.call('PEXPIRE', userKey, ttl)
                redis.call(
                    'HSET', tokenKey,
                    '${WaitingQueueRedisConstants.STATUS_FIELD}', '${WaitingQueueRedisConstants.ACTIVE_STATUS}',
                    '${WaitingQueueRedisConstants.TOKEN_FIELD}', token,
                    '${WaitingQueueRedisConstants.USER_ID_FIELD}', userId,
                    '${WaitingQueueRedisConstants.SEQUENCE_FIELD}', sequence,
                    '${WaitingQueueRedisConstants.AVAILABLE_AT_FIELD}', availableAt,
                    '${WaitingQueueRedisConstants.EXPIRES_AT_FIELD}', expiresAt
                )
                redis.call('PEXPIRE', tokenKey, ttl)
                table.insert(result, userId)
                table.insert(result, sequence)
            else
                redis.call('ZADD', KEYS[1], sequence, userId)
            end
        end
        return result
    """

    const val VALIDATE_AND_RESERVE_TOKEN = """
        local status = redis.call('HGET', KEYS[1], '${WaitingQueueRedisConstants.STATUS_FIELD}')
        if not status then
            return ${WaitingQueueRedisConstants.TOKEN_INVALID}
        end
        if redis.call('HGET', KEYS[1], '${WaitingQueueRedisConstants.USER_ID_FIELD}') ~= ARGV[1] then
            return ${WaitingQueueRedisConstants.TOKEN_INVALID}
        end
        if tonumber(redis.call('HGET', KEYS[1], '${WaitingQueueRedisConstants.AVAILABLE_AT_FIELD}')) > tonumber(ARGV[4]) then
            return ${WaitingQueueRedisConstants.TOKEN_NOT_YET_AVAILABLE}
        end
        local reservedBy = redis.call('HGET', KEYS[1], '${WaitingQueueRedisConstants.IDEMPOTENCY_KEY_FIELD}')
        if status == '${WaitingQueueRedisConstants.CONSUMED_STATUS}' then
            if reservedBy == ARGV[2] then
                return ${WaitingQueueRedisConstants.TOKEN_CONSUMED_BY_SAME_KEY}
            end
            return ${WaitingQueueRedisConstants.TOKEN_RESERVED_OR_CONSUMED_BY_OTHER_KEY}
        end
        if status == '${WaitingQueueRedisConstants.PROCESSING_STATUS}' then
            if reservedBy == ARGV[2] then
                return ${WaitingQueueRedisConstants.TOKEN_PROCESSING_BY_SAME_KEY}
            end
            return ${WaitingQueueRedisConstants.TOKEN_RESERVED_OR_CONSUMED_BY_OTHER_KEY}
        end
        if status ~= '${WaitingQueueRedisConstants.ACTIVE_STATUS}' then
            return ${WaitingQueueRedisConstants.TOKEN_INVALID}
        end
        if redis.call('HGET', KEYS[2], '${WaitingQueueRedisConstants.TOKEN_FIELD}') ~= ARGV[3] then
            return ${WaitingQueueRedisConstants.TOKEN_INVALID}
        end
        redis.call('HSET', KEYS[1], '${WaitingQueueRedisConstants.STATUS_FIELD}', '${WaitingQueueRedisConstants.PROCESSING_STATUS}', '${WaitingQueueRedisConstants.IDEMPOTENCY_KEY_FIELD}', ARGV[2])
        redis.call('HSET', KEYS[2], '${WaitingQueueRedisConstants.STATUS_FIELD}', '${WaitingQueueRedisConstants.PROCESSING_STATUS}', '${WaitingQueueRedisConstants.IDEMPOTENCY_KEY_FIELD}', ARGV[2])
        return ${WaitingQueueRedisConstants.TOKEN_VALID}
    """

    const val CONSUME_TOKEN = """
        local status = redis.call('HGET', KEYS[1], '${WaitingQueueRedisConstants.STATUS_FIELD}')
        if not status or redis.call('HGET', KEYS[1], '${WaitingQueueRedisConstants.USER_ID_FIELD}') ~= ARGV[1] then
            return 0
        end
        local reservedBy = redis.call('HGET', KEYS[1], '${WaitingQueueRedisConstants.IDEMPOTENCY_KEY_FIELD}')
        if status == '${WaitingQueueRedisConstants.CONSUMED_STATUS}' then
            if reservedBy == ARGV[2] then
                return 1
            end
            return 0
        end
        if status ~= '${WaitingQueueRedisConstants.PROCESSING_STATUS}' or reservedBy ~= ARGV[2] then
            return 0
        end
        redis.call('HSET', KEYS[1], '${WaitingQueueRedisConstants.STATUS_FIELD}', '${WaitingQueueRedisConstants.CONSUMED_STATUS}', '${WaitingQueueRedisConstants.IDEMPOTENCY_KEY_FIELD}', ARGV[2])
        redis.call('DEL', KEYS[2])
        return 1
    """

    const val RELEASE_TOKEN = """
        if redis.call('HGET', KEYS[1], '${WaitingQueueRedisConstants.STATUS_FIELD}') ~= '${WaitingQueueRedisConstants.PROCESSING_STATUS}' then
            return 0
        end
        if redis.call('HGET', KEYS[1], '${WaitingQueueRedisConstants.USER_ID_FIELD}') ~= ARGV[1] then
            return 0
        end
        if redis.call('HGET', KEYS[1], '${WaitingQueueRedisConstants.IDEMPOTENCY_KEY_FIELD}') ~= ARGV[2] then
            return 0
        end
        redis.call('HSET', KEYS[1], '${WaitingQueueRedisConstants.STATUS_FIELD}', '${WaitingQueueRedisConstants.ACTIVE_STATUS}')
        redis.call('HDEL', KEYS[1], '${WaitingQueueRedisConstants.IDEMPOTENCY_KEY_FIELD}')
        if redis.call('EXISTS', KEYS[2]) == 1 then
            redis.call('HSET', KEYS[2], '${WaitingQueueRedisConstants.STATUS_FIELD}', '${WaitingQueueRedisConstants.ACTIVE_STATUS}')
            redis.call('HDEL', KEYS[2], '${WaitingQueueRedisConstants.IDEMPOTENCY_KEY_FIELD}')
        end
        return 1
    """
}
