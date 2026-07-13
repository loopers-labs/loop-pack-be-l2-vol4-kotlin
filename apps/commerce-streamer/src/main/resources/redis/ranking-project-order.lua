local isNew = redis.call('SADD', KEYS[1], ARGV[1])
if isNew == 0 then
    return 0
end

local weights = redis.call('HMGET', KEYS[8], 'view', 'like', 'sales')
local viewWeight = tonumber(weights[1]) or tonumber(ARGV[3])
local likeWeight = tonumber(weights[2]) or tonumber(ARGV[4])
local salesWeight = tonumber(weights[3]) or tonumber(ARGV[5])

for index = 6, #ARGV, 2 do
    local productId = ARGV[index]
    local amount = ARGV[index + 1]
    local accumulatedAmount = redis.call('HINCRBY', KEYS[2], productId, amount)
    local salesScore = math.log(1 + tonumber(accumulatedAmount))
    redis.call('ZADD', KEYS[3], salesScore, productId)

    local viewScore = tonumber(redis.call('ZSCORE', KEYS[4], productId)) or 0
    local likeScore = tonumber(redis.call('ZSCORE', KEYS[5], productId)) or 0
    local carryScore = tonumber(redis.call('ZSCORE', KEYS[6], productId)) or 0
    local finalScore = carryScore + (viewScore * viewWeight) + (likeScore * likeWeight) + (salesScore * salesWeight)
    redis.call('ZADD', KEYS[7], finalScore, productId)
end

for index = 1, 7 do
    if redis.call('EXISTS', KEYS[index]) == 1 then
        redis.call('EXPIREAT', KEYS[index], ARGV[2])
    end
end

return 1
