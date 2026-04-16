# 秒杀压测时如何用 Arthas 找瓶颈

这套项目的秒杀链路不是直接下单落库，而是：

1. `VoucherOrderServiceImpl.seckillVoucher()`
2. Redis Lua 校验库存和一人一单
3. `MQSender.sendSeckillMessage()`
4. RabbitMQ 异步消费
5. `MQReceiver.receiveSeckillMessage()`
6. MySQL 扣库存并保存订单

所以压测时要分清楚你卡在了哪一段。

## 先看三个总览命令

```text
dashboard
thread -n 20
memory
```

怎么看：

- `CPU` 很高，同时 `thread -n 20` 里有业务线程持续占用，说明应用本身在忙
- `CPU` 不高，但请求很多失败，先怀疑限流、MQ、Redis、连接池等待
- `heap` 涨得快，配合 `gc` 抖动明显，说明对象分配或日志过多

## 第一层：入口是不是被限流拦住了

```text
monitor -c 5 com.market.aspect.RateLimitAspect around
watch com.market.aspect.RateLimitAspect around "{params[0],returnObj}" -x 2 -n 5
```

怎么看：

- 如果 `around` 调用很多，但 `seckillVoucher()` 调用不高，先不是 CPU 瓶颈，而是限流规则太低
- 你现在代码里 `voucher-order-seckill` 注解默认是 `permitsPerSecond = 10`
- 想压到机器难受，必须先把这条限流放大，否则压测打到的大部分只是限流器

## 第二层：入口方法慢不慢

```text
monitor -c 5 com.market.service.impl.VoucherOrderServiceImpl seckillVoucher
trace com.market.service.impl.VoucherOrderServiceImpl seckillVoucher '#cost > 10'
watch com.market.service.impl.VoucherOrderServiceImpl seckillVoucher "{params,returnObj}" -x 2 -n 5
```

怎么看：

- 如果 `seckillVoucher()` 很快，通常说明入口不是瓶颈，压力在 MQ 消费端
- 如果这里慢，重点看 Redis Lua 执行、JSON 序列化、MQ 发送
- `watch` 里如果大量是“库存不足”或“重复下单”，那也不是系统扛不住，而是业务被打穿或 token 不够多

## 第三层：看 MQ 发送端

```text
monitor -c 5 com.market.rebbitmq.MQSender sendSeckillMessage
trace com.market.rebbitmq.MQSender sendSeckillMessage '#cost > 5'
```

怎么看：

- 如果这里耗时突然上升，说明 RabbitMQ 或网络开始拖慢入口
- 入口慢但 Redis 不慢时，发送 MQ 往往是重点怀疑对象

## 第四层：真正容易成为瓶颈的是消费端

```text
monitor -c 5 com.market.rebbitmq.MQReceiver receiveSeckillMessage
trace com.market.rebbitmq.MQReceiver receiveSeckillMessage '#cost > 20'
watch com.market.rebbitmq.MQReceiver receiveSeckillMessage "{params,throwExp}" -e -x 2 -n 5
```

怎么看：

- 如果 `seckillVoucher()` 很快，但 `receiveSeckillMessage()` 耗时高，瓶颈就在消费端
- 这个方法里有：
  - 查重复订单
  - MySQL CAS 扣库存
  - 保存订单
- 这里通常会受 MySQL、索引、事务、连接池影响最大

## 第五层：直接看 CPU 火焰图

压测开始后先执行：

```text
profiler start
```

压 30 到 60 秒后执行：

```text
profiler stop --format html
```

怎么看：

- 如果热点在 `org.springframework.data.redis`，说明 Redis/序列化占比高
- 如果热点在 `com.rabbitmq` 或 `spring amqp`，说明 MQ 是重点
- 如果热点在 `mysql` 驱动、`mybatis`、`com.baomidou`，说明数据库链路是重点
- 如果热点在 `logback`，说明日志太重，压测时日志 I/O 本身就是瓶颈

## 我建议你压测时同时盯的 5 个点

1. `RateLimitAspect.around`
2. `VoucherOrderServiceImpl.seckillVoucher`
3. `MQSender.sendSeckillMessage`
4. `MQReceiver.receiveSeckillMessage`
5. `profiler` 火焰图

## 一句判断逻辑

- 请求很多，但 CPU 不高：先看限流和等待
- 入口快，消费慢：瓶颈在 MQ 后半段和 MySQL
- 入口慢，发送也慢：瓶颈在 Redis/MQ 前半段
- CPU 高且火焰图全是日志/序列化：不是业务算法，是框架和 I/O 开销
