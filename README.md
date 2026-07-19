# Market

Market 是一个面向优惠券与秒杀场景的全栈示例项目，重点演示高并发下单、库存一致性、异步处理、限流熔断、任务调度和可观测性。

## 主要能力

- 用户注册、登录与 JWT 鉴权
- 优惠券浏览、下单与订单查询
- Redis 缓存、分布式锁和秒杀库存处理
- RabbitMQ 异步消息与 Canal 数据同步
- Sentinel / Nacos 限流配置
- XXL-JOB 定时任务
- Prometheus、Grafana 与 Spring Boot Actuator 监控
- k6 压测脚本和历史性能报告

## 技术栈

- 后端：Java 8、Spring Boot 2.3、MyBatis-Plus
- 前端：Vue 3、Vue Router、Vite
- 基础设施：MySQL、Redis、RabbitMQ、Canal、Nacos、Sentinel、XXL-JOB
- 可观测性：Prometheus、Grafana、k6

## 目录结构

```text
market/
├─ backend/
│  ├─ src/                  # Spring Boot 应用
│  ├─ loadtest/             # k6 场景与令牌生成工具
│  ├─ tools/                # Arthas 等诊断资料
│  └─ docker-compose.yml    # 本地基础设施
├─ frontend/                # Vue 前端
└─ docs/                    # API 文档
```

## 本地启动

### 1. 准备配置

敏感配置通过环境变量提供：

```bash
export MYSQL_PASSWORD="your-password"
export REDIS_PASSWORD="your-password"
export JWT_SECRET="replace-with-a-random-secret"
export MAIL_USERNAME="your-email"
export MAIL_PASSWORD="your-mail-app-password"
```

PowerShell 使用 `$env:变量名="值"`。

### 2. 启动基础设施

```bash
cd backend
docker compose up -d mysql redis rabbitmq
```

数据库脚本位于 `backend/src/main/resources/db/market.sql`。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认监听 `http://localhost:8081`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

## 文档与压测

- API 说明：[`docs/API接口文档.md`](docs/API接口文档.md)
- 性能分析：[`backend/tools/arthas/SECKILL_BOTTLENECK.md`](backend/tools/arthas/SECKILL_BOTTLENECK.md)
- k6 环境示例：[`backend/loadtest/k6/env.example.ps1`](backend/loadtest/k6/env.example.ps1)

压测令牌和本机运行上下文属于临时敏感文件，不应提交到 Git。

## 安全说明

公开部署前必须替换默认数据库、Redis、RabbitMQ、Nacos 和 JWT 配置。若邮箱授权码或令牌曾进入公开提交历史，请立即轮换。
