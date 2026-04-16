# 秒杀接口压力测试报告

测试时间：2026-04-16  
测试接口：`POST /voucher-order/seckill/{voucherId}`  
测试券：`voucherId=7`  
测试脚本：`backend/loadtest/k6/seckill.js`  
发压工具：Grafana k6，Docker 容器内运行  

## 1. 测试结论

在本次机器与 JVM 配置下，秒杀接口的稳定承载上限约为：

| 指标 | 结论 |
| --- | --- |
| 最高稳定 QPS | 约 780 QPS |
| 建议保守可持续 QPS | 约 700 QPS |
| 开始出现丢压的目标 QPS | 约 790 QPS |
| 明显过载目标 QPS | 1000 QPS 及以上 |
| 主要过载表现 | k6 `dropped_iterations` 增加，接口延迟堆积 |
| HTTP 失败率 | 本轮均为 0% |

判断依据：目标 780 QPS 时无 `dropped_iterations`，HTTP 失败率 0%，p95 约 877 ms；目标 790 QPS 起开始出现 `dropped_iterations`，说明系统或发压链路已无法稳定维持目标到达率。

## 2. 测试机器配置

| 项目 | 配置 |
| --- | --- |
| 机器型号 | HP ZBook Fury 16 G9 Mobile Workstation PC |
| CPU | 12th Gen Intel Core i9-12950HX |
| CPU 核心/线程 | 16 核 / 24 逻辑处理器 |
| 物理内存 | 约 32 GB |
| 操作系统 | Windows 11 专业版 |
| OS 版本 | 10.0.26200，64 位 |
| 测试时可用物理内存 | 约 10.8 GB |

Docker Desktop 配置：

| 项目 | 配置 |
| --- | --- |
| Docker Server | 29.2.1 |
| Docker Desktop CPU | 24 |
| Docker Desktop Memory | 约 15.42 GiB |

## 3. JVM 与应用运行配置

应用通过 `mvn spring-boot:run` 启动，主类为：

```text
com.market.MarketApplication
```

Java 版本：

```text
OpenJDK 17.0.18 LTS, Microsoft build 17.0.18+8-LTS
```

JVM 显式参数：

```text
-XX:TieredStopAtLevel=1
```

JVM 实际堆配置由 Ergonomics 自动计算：

| JVM 参数 | 值 |
| --- | --- |
| GC | G1GC |
| InitialHeapSize | 532,676,608 bytes，约 508 MB |
| MaxHeapSize | 8,489,271,296 bytes，约 7.91 GB |
| SoftMaxHeapSize | 8,489,271,296 bytes，约 7.91 GB |
| G1HeapRegionSize | 4 MB |
| CICompilerCount | 12 |

测试后 JVM 内存状态：

| 项目 | 值 |
| --- | --- |
| Java 进程 Working Set | 约 759 MB |
| Java 进程 Peak Working Set | 约 968 MB |
| Java 进程 Private Memory | 约 1.00 GB |
| G1 heap total | 324 MB |
| G1 heap used | 约 210 MB |
| Metaspace used | 约 75 MB |

## 4. 应用与中间件配置

应用端口：

```yaml
server:
  port: 8081
```

Redis 配置：

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: 1234
    database: 6
    lettuce:
      pool:
        max-active: 10
        max-idle: 10
        min-idle: 1
```

MySQL 配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/market?useSSL=false&serverTimezone=UTC
    username: root
```

限流配置：

```yaml
rate-limit:
  enabled: ${RATE_LIMIT_ENABLED:false}
  type: ${RATE_LIMIT_TYPE:sentinel}
```

本轮测试未显式设置 `RATE_LIMIT_ENABLED=true`，因此业务限流切面处于关闭状态。

中间件版本：

| 组件 | 版本 |
| --- | --- |
| Redis | 6.2.21 |
| MySQL | 5.7.44 |
| RabbitMQ | 3.9.29 |
| Spring Boot | 2.3.12.RELEASE |
| Spring Data Redis | 2.6.2 |
| Lettuce | 6.1.6.RELEASE |
| MyBatis Plus | 3.4.3 |
| Sentinel | 1.8.6 |
| Micrometer Prometheus | 1.5.14 |

## 5. 测试方法

压测模式使用 k6 `ramping-arrival-rate`，即按目标请求到达率发压，而不是简单增加虚拟用户数。

本轮修正了脚本参数命名，避免 `K6_*` 环境变量被 k6 运行器解释为自身配置，导致脚本场景被覆盖。当前脚本优先读取：

```text
LOAD_SCENARIO_MODE
LOAD_START_RATE
LOAD_PRE_ALLOCATED_VUS
LOAD_MAX_VUS
LOAD_STAGES
LOAD_SLEEP_MS
```

典型命令形态：

```powershell
docker compose --profile loadtest run --rm `
  -e BASE_URL=http://host.docker.internal:8081 `
  -e VOUCHER_ID=7 `
  -e AUTH_TOKENS_FILE=/scripts/generated/auth_tokens.txt `
  k6 run `
  -o experimental-prometheus-rw `
  --env LOAD_SCENARIO_MODE=ramping-arrival-rate `
  --env LOAD_START_RATE=780 `
  --env LOAD_PRE_ALLOCATED_VUS=780 `
  --env LOAD_MAX_VUS=2500 `
  --env LOAD_STAGES="10s:780,30s:780,5s:0" `
  --env LOAD_SLEEP_MS=0 `
  --summary-export /scripts/generated/summary-constant-780.json `
  /scripts/seckill.js
```

## 6. 测试结果

| 测试文件 | 请求数 | 平均完成 QPS | p95 延迟 | 平均延迟 | HTTP 失败率 | dropped_iterations | 最大 VU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `summary-constant-500.json` | 21,249 | 449.52 | 7.96 ms | 5.71 ms | 0% | 0 | 500 |
| `summary-constant-700.json` | 29,749 | 626.17 | 23.16 ms | 8.67 ms | 0% | 0 | 700 |
| `summary-constant-720.json` | 30,599 | 620.12 | 63.22 ms | 15.63 ms | 0% | 0 | 720 |
| `summary-constant-780.json` | 33,149 | 666.66 | 877.19 ms | 237.89 ms | 0% | 0 | 780 |
| `summary-constant-790.json` | 32,620 | 659.17 | 1,378.40 ms | 840.51 ms | 0% | 954 | 970 |
| `summary-constant-800.json` | 32,989 | 665.47 | 1,432.98 ms | 889.00 ms | 0% | 1,010 | 978 |
| `summary-constant-820.json` | 32,797 | 663.33 | 2,131.60 ms | 1,224.90 ms | 0% | 2,052 | 1,161 |
| `summary-constant-850.json` | 32,340 | 686.51 | 2,091.27 ms | 1,453.14 ms | 0% | 3,784 | 1,398 |
| `summary-constant-1000.json` | 34,336 | 719.27 | 2,764.48 ms | 2,028.04 ms | 0% | 8,163 | 2,102 |
| `summary-step-1k-8k.json` | 68,275 | 752.39 | 6,941.10 ms | 2,679.57 ms | 0% | 224,224 | 3,742 |

说明：平均完成 QPS 包含 ramp up 和 ramp down 阶段，因此会低于目标 QPS。是否稳定主要看目标恒定阶段是否出现 `dropped_iterations`、p95 是否持续恶化、HTTP 失败率是否上升。

## 7. 压测后业务状态

Redis DB 6：

| Key | 值 |
| --- | ---: |
| `seckill:stock:7` | 96,258 |
| `seckill:order:7` 集合大小 | 3,742 |

MySQL：

| 项目 | 值 |
| --- | ---: |
| `tb_seckill_voucher.stock` | 96,258 |
| `tb_voucher_order` 中 `voucher_id=7` 订单数 | 3,742 |

Redis 与 MySQL 在本轮测试后保持一致。

## 8. 结果解读

1. 700 QPS 以内表现稳定，p95 在几十毫秒内，且没有 dropped iterations。
2. 780 QPS 是本轮测到的最高稳定目标值，没有 dropped iterations，但 p95 已升至约 877 ms，说明已经接近边界。
3. 790 QPS 起出现 dropped iterations，说明目标请求到达率无法被稳定维持。
4. 1000 QPS 虽然 HTTP 失败率仍为 0%，但 p95 接近 2.8 秒，并且 dropped iterations 达到 8,163，已经不适合作为稳定承载值。
5. 1k 到 8k 的拉升测试中，p95 达到约 6.94 秒，dropped iterations 达到 224,224，系统明显进入过载区。

## 9. 限制与注意事项

1. 本轮只有约 4,000 个压测 token。前 3,742 个用户成功下单后，后续请求主要进入 Lua 的“重复下单”分支。因此本报告更准确描述的是当前秒杀接口在 Redis Lua 校验/判重路径下的承载能力。
2. 当前应用日志中 Spring MVC DEBUG 日志较多，会显著影响高并发性能。关闭 DEBUG 后，结果可能提升。
3. 本次应用通过 `mvn spring-boot:run` 启动，并带有 `-XX:TieredStopAtLevel=1`，这不是生产 JVM 启动方式。打包 jar 并使用生产 JVM 参数后，结果可能不同。
4. Docker、MySQL、Redis、RabbitMQ、Prometheus、Grafana、Nacos、Sentinel Dashboard 等服务运行在同一台机器上，会共享 CPU、内存和 I/O。
5. 限流组件未启用，报告结果不代表开启 Sentinel/Guava 限流后的吞吐。

## 10. 后续建议

1. 生成 20,000 到 50,000 个唯一用户 token，新建压测券，单独测试“全部成功下单 + MQ 落库”链路。
2. 关闭 DEBUG 日志后重复 700、780、820、1000 QPS 测试，确认日志对性能的影响。
3. 使用生产启动方式测试，例如 `java -jar` 并显式设置 `-Xms/-Xmx`，避免 `mvn spring-boot:run` 和 `TieredStopAtLevel=1` 影响结果。
4. 分别观察 Redis、Tomcat 线程池、RabbitMQ 消费堆积、MySQL 写入耗时，定位 790 QPS 附近开始丢压的具体瓶颈。
