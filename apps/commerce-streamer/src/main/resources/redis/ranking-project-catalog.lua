local isNew = redis.call('SADD', KEYS[1], ARGV[1])
if isNew == 0 then
    return 0
end

local productId = ARGV[2]
redis.call('ZINCRBY', KEYS[2], ARGV[3], productId)

local weights = redis.call('HMGET', KEYS[8], 'view', 'like', 'sales')
local viewWeight = tonumber(weights[1]) or tonumber(ARGV[5])
local likeWeight = tonumber(weights[2]) or tonumber(ARGV[6])
local salesWeight = tonumber(weights[3]) or tonumber(ARGV[7])
local viewScore = tonumber(redis.call('ZSCORE', KEYS[3], productId)) or 0
local likeScore = tonumber(redis.call('ZSCORE', KEYS[4], productId)) or 0
local salesScore = tonumber(redis.call('ZSCORE', KEYS[5], productId)) or 0
local carryScore = tonumber(redis.call('ZSCORE', KEYS[6], productId)) or 0
local finalScore = carryScore + (viewScore * viewWeight) + (likeScore * likeWeight) + (salesScore * salesWeight)

redis.call('ZADD', KEYS[7], finalScore, productId)
for index = 1, 7 do
    if redis.call('EXISTS', KEYS[index]) == 1 then
        redis.call('EXPIREAT', KEYS[index], ARGV[4])
    end
end

return 1
