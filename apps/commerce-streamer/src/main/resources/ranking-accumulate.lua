-- KEYS: [1] today key · [2] tail key · [3] idempotency key
-- ARGV: [1] idempotency TTL(sec) · [2] today EXPIREAT(epoch sec) · [3] tail EXPIREAT(epoch sec)
--       [4] tail flag(1 = wall clock in [23:50, 00:00)) · [5..] (productId, delta) pairs
if not redis.call('SET', KEYS[3], '1', 'NX', 'EX', ARGV[1]) then
  return 0
end
local tail = ARGV[4] == '1'
for i = 5, #ARGV, 2 do
  redis.call('ZINCRBY', KEYS[1], ARGV[i + 1], ARGV[i])
  if tail then redis.call('ZINCRBY', KEYS[2], ARGV[i + 1], ARGV[i]) end
end
if redis.call('PTTL', KEYS[1]) == -1 then redis.call('EXPIREAT', KEYS[1], ARGV[2]) end
if tail and redis.call('PTTL', KEYS[2]) == -1 then redis.call('EXPIREAT', KEYS[2], ARGV[3]) end
return 1
