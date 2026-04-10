-- 1. 参数列表
-- 1.1 优惠券 id
local voucherId = ARGV[1]
-- 1.2 用户 id
local userId = ARGV[2]

-- 2. 数据 key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
local stock = tonumber(redis.call('get', stockKey))

-- 3. 脚本业务
-- 3.1 库存不存在或不足时直接返回
if (not stock) or stock <= 0 then
    return 1
end

-- 3.2 判断用户是否已经下单
if redis.call('sismember', orderKey, userId) == 1 then
    return 2
end

-- 3.3 扣减库存并记录下单用户
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0
