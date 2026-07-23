redis.call('HSET', KEYS[1],
    'view', ARGV[1],
    'like', ARGV[2],
    'sales', ARGV[3])

redis.call('ZUNIONSTORE', KEYS[6], 4,
    KEYS[2], KEYS[3], KEYS[4], KEYS[5],
    'WEIGHTS', 1, ARGV[1], ARGV[2], ARGV[3],
    'AGGREGATE', 'SUM')

if redis.call('EXISTS', KEYS[6]) == 1 then
    redis.call('EXPIREAT', KEYS[6], ARGV[4])
end

return redis.call('ZCARD', KEYS[6])
