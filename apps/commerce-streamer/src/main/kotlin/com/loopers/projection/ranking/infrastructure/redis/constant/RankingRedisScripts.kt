package com.loopers.projection.ranking.infrastructure.redis.constant

object RankingRedisScripts {
    const val INCREMENT_SCORE = """
        if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[3]) == false then
            return 0
        end
        redis.call('ZINCRBY', KEYS[2], ARGV[1], ARGV[2])
        redis.call('EXPIRE', KEYS[2], ARGV[3], 'NX')
        return 1
    """

    const val CARRY_OVER = """
        if redis.call('EXISTS', KEYS[2]) == 0 then
            return -1
        end
        if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[3]) == false then
            return 0
        end
        redis.call('ZUNIONSTORE', KEYS[3], 1, KEYS[2], 'WEIGHTS', ARGV[1])
        redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', '(' .. ARGV[2])
        redis.call('EXPIRE', KEYS[3], ARGV[3])
        return 1
    """
}
