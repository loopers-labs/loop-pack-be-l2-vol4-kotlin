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
}
