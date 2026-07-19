local ranked = redis.call('ZREVRANGE', KEYS[1], 0, tonumber(ARGV[1]) - 1, 'WITHSCORES')
if #ranked == 0 then
    return 0
end

redis.call('DEL', KEYS[2])
for index = 1, #ranked, 2 do
    local productId = ranked[index]
    local carryScore = tonumber(ranked[index + 1]) * tonumber(ARGV[2])
    redis.call('ZADD', KEYS[2], carryScore, productId)
end

local weights = redis.call('HMGET', KEYS[7], 'view', 'like', 'sales')
local viewWeight = tonumber(weights[1]) or tonumber(ARGV[3])
local likeWeight = tonumber(weights[2]) or tonumber(ARGV[4])
local salesWeight = tonumber(weights[3]) or tonumber(ARGV[5])

redis.call('ZUNIONSTORE', KEYS[6], 4,
    KEYS[2], KEYS[3], KEYS[4], KEYS[5],
    'WEIGHTS', 1, viewWeight, likeWeight, salesWeight,
    'AGGREGATE', 'SUM')
redis.call('EXPIREAT', KEYS[2], ARGV[6])
redis.call('EXPIREAT', KEYS[6], ARGV[6])

return redis.call('ZCARD', KEYS[2])
