# 优惠券商城 - API 接口文档

> 基础路径：`http://localhost:8081`
>
> 所有响应均为统一结构 `Result`，日期时间格式为 `yyyy-MM-ddTHH:mm:ss`

---

## 目录

- [1. 通用说明](#1-通用说明)
- [2. 用户模块 `/user`](#2-用户模块-user)
- [3. 优惠券模块 `/voucher`](#3-优惠券模块-voucher)
- [4. 订单模块 `/voucher-order`](#4-订单模块-voucher-order)
- [5. 数据模型](#5-数据模型)

---

## 1. 通用说明

### 1.1 统一响应结构

```json
{
  "success": true,        // 是否成功
  "errorMsg": null,        // 失败时的错误信息
  "data": {},              // 响应数据
  "total": null            // 列表总条数（仅列表接口可能返回）
}
```

### 1.2 认证方式

- 登录成功后返回 `access_token` 和 `refresh_token`
- 请求时在 Header 中携带：
  ```
  authorization: Bearer <access_token>
  ```
- Token 过期时使用 `POST /user/refresh` 刷新

### 1.3 公开接口（无需登录）

| 路径 |
|------|
| `POST /user/login` |
| `POST /user/code` |
| `POST /user/register` |
| `POST /user/password` |
| `POST /user/refresh` |
| `/voucher/**`（全部优惠券接口） |

> 其余接口均需携带 Token。

---

## 2. 用户模块 `/user`

### 2.1 发送邮箱验证码

`POST /user/code`

**是否需要登录**：否

**请求参数**（Query String）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | 是 | 邮箱地址 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 2.2 用户注册

`POST /user/register`

**是否需要登录**：否

**请求体**（JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | 是 | 邮箱地址 |
| password | String | 是 | 密码 |
| code | String | 是 | 邮箱验证码 |
| nickName | String | 否 | 昵称 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 2.3 修改密码

`POST /user/password`

**是否需要登录**：否

**请求体**（JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | 是 | 邮箱地址 |
| code | String | 是 | 邮箱验证码 |
| password | String | 是 | 新密码 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 2.4 登录

`POST /user/login`

**是否需要登录**：否

**请求体**（JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | String | 是 | 邮箱地址 |
| password | String | 是 | 密码 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

---

### 2.5 刷新 Token

`POST /user/refresh`

**是否需要登录**：否（使用 refresh_token）

**请求体**（JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| refreshToken | String | 是 | 刷新令牌 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9...",
    "refresh_token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

---

### 2.6 登出

`POST /user/logout`

**是否需要登录**：是

**请求参数**：无

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 2.7 查询用户信息

`GET /user/{id}`

**是否需要登录**：是

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户 ID |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": {
    "id": 1,
    "nickName": "张三",
    "email": "zhangsan@example.com"
  }
}
```

---

### 2.8 查询当前登录用户信息

`GET /user/me`

**是否需要登录**：是

**请求参数**：无

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": {
    "id": 1,
    "nickName": "张三",
    "email": "zhangsan@example.com"
  }
}
```

---

### 2.9 修改昵称

`PUT /user`

**是否需要登录**：是

**请求体**（JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| nickName | String | 是 | 新昵称 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

## 3. 优惠券模块 `/voucher`

> 此模块所有接口均为公开接口，无需登录。

### 3.1 新增普通券

`POST /voucher`

**请求体**（JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 是 | 券标题 |
| subTitle | String | 否 | 副标题 |
| rules | String | 否 | 使用规则 |
| payValue | Long | 是 | 支付金额（单位：分） |
| actualValue | Long | 是 | 抵扣金额（单位：分） |
| type | Integer | 是 | 优惠券类型：`0`=普通券 |
| status | Integer | 否 | 状态：`1`=上架，`2`=下架，`3`=过期 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": 1
}
```

> `data` 返回新创建的优惠券 ID。

---

### 3.2 新增秒杀券

`POST /voucher/seckill`

**请求体**（JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 是 | 券标题 |
| subTitle | String | 否 | 副标题 |
| rules | String | 否 | 使用规则 |
| payValue | Long | 是 | 支付金额（单位：分） |
| actualValue | Long | 是 | 抵扣金额（单位：分） |
| type | Integer | 是 | 优惠券类型：`1`=秒杀券 |
| status | Integer | 否 | 状态 |
| stock | Integer | 是 | 库存数量（秒杀券特有） |
| beginTime | String | 是 | 生效时间（格式：`yyyy-MM-ddTHH:mm:ss`） |
| endTime | String | 是 | 失效时间（格式：`yyyy-MM-ddTHH:mm:ss`） |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": 2
}
```

---

### 3.3 更新优惠券

`PUT /voucher`

**请求体**（JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 优惠券 ID |
| title | String | 否 | 券标题 |
| subTitle | String | 否 | 副标题 |
| rules | String | 否 | 使用规则 |
| payValue | Long | 否 | 支付金额（单位：分） |
| actualValue | Long | 否 | 抵扣金额（单位：分） |
| type | Integer | 否 | 优惠券类型 |
| status | Integer | 否 | 状态 |

> 只传需要更新的字段即可，更新 MySQL 后 Canal 自动同步 Redis。

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 3.4 删除优惠券

`DELETE /voucher/{id}`

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 优惠券 ID |

> 删除 MySQL 后 Canal 自动清理 Redis。

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 3.5 更新秒杀券库存

`PUT /voucher/stock/{id}`

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 优惠券 ID |

**请求参数**（Query String）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| stock | Integer | 是 | 新的库存数量 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 3.6 查询优惠券列表

`GET /voucher/list`

**请求参数**（Query String，均可选）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | Integer | 否 | 优惠券类型筛选：`0`=普通券，`1`=秒杀券 |
| status | Integer | 否 | 状态筛选：`1`=上架，`2`=下架，`3`=过期 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": [
    {
      "id": 1,
      "title": "满100减20",
      "subTitle": "全场通用",
      "rules": "满100元可用",
      "payValue": 800,
      "actualValue": 2000,
      "type": 0,
      "status": 1,
      "createTime": "2026-04-01T10:00:00",
      "updateTime": "2026-04-01T10:00:00"
    },
    {
      "id": 2,
      "title": "限时秒杀券",
      "subTitle": "数量有限",
      "rules": "秒杀专用",
      "payValue": 500,
      "actualValue": 1500,
      "type": 1,
      "status": 1,
      "stock": 100,
      "beginTime": "2026-04-01T00:00:00",
      "endTime": "2026-04-30T23:59:59",
      "createTime": "2026-04-01T10:00:00",
      "updateTime": "2026-04-01T10:00:00"
    }
  ]
}
```

---

### 3.7 查询优惠券详情

`GET /voucher/{id}`

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 优惠券 ID |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": {
    "id": 1,
    "title": "满100减20",
    "subTitle": "全场通用",
    "rules": "满100元可用",
    "payValue": 800,
    "actualValue": 2000,
    "type": 0,
    "status": 1,
    "createTime": "2026-04-01T10:00:00",
    "updateTime": "2026-04-01T10:00:00"
  }
}
```

> 秒杀券额外返回 `stock`、`beginTime`、`endTime` 字段。

---

### 3.8 优惠券上下架

`PUT /voucher/{id}/status`

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 优惠券 ID |

**请求参数**（Query String）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | 是 | 目标状态：`1`=上架，`2`=下架，`3`=过期 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

## 4. 订单模块 `/voucher-order`

> 此模块所有接口均需要登录。

### 4.1 秒杀购买

`POST /voucher-order/seckill/{id}`

**是否需要登录**：是

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 秒杀券 ID |

**请求参数**：无

> 秒杀下单走 MQ 异步处理，接口立即返回订单 ID，实际订单创建在后台完成。

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": "160000000000000001"
}
```

> `data` 为订单 ID（雪花算法生成，String 类型）。前端拿到后可通过 `GET /voucher-order/{id}` 查询是否创建成功。

---

### 4.2 查询我的订单列表

`GET /voucher-order/my`

**是否需要登录**：是

**请求参数**：无

> 返回当前登录用户的所有优惠券订单。

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": [
    {
      "id": "160000000000000001",
      "userId": 1,
      "voucherId": 2,
      "payType": 2,
      "status": 1,
      "createTime": "2026-04-08T20:00:00",
      "payTime": null,
      "useTime": null,
      "refundTime": null,
      "updateTime": "2026-04-08T20:00:00"
    }
  ]
}
```

---

### 4.3 查询订单详情

`GET /voucher-order/{id}`

**是否需要登录**：是

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单 ID |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": {
    "id": "160000000000000001",
    "userId": 1,
    "voucherId": 2,
    "payType": 2,
    "status": 1,
    "createTime": "2026-04-08T20:00:00",
    "payTime": null,
    "useTime": null,
    "refundTime": null,
    "updateTime": "2026-04-08T20:00:00"
  }
}
```

---

### 4.4 购买普通券

`POST /voucher-order/{id}`

**是否需要登录**：是

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 普通券 ID（type=0） |

**请求参数**：无

> 创建订单（状态为未支付），同一用户对同一优惠券仅限一单（一人一单限制）。

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": "160000000000000002"
}
```

> `data` 为订单 ID。

---

### 4.5 订单支付

`PUT /voucher-order/{id}/pay`

**是否需要登录**：是

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单 ID |

**请求参数**（Query String）

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| payType | Integer | 是 | 支付方式：`1`=余额，`2`=支付宝，`3`=微信 |

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 4.6 取消订单

`PUT /voucher-order/{id}/cancel`

**是否需要登录**：是

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单 ID |

**请求参数**：无

> 仅「未支付」状态（status=1）的订单可取消。

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 4.7 核销订单

`PUT /voucher-order/{id}/use`

**是否需要登录**：是

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单 ID |

**请求参数**：无

> 仅「已支付」状态（status=2）的订单可核销。

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 4.8 申请退款

`PUT /voucher-order/{id}/refund`

**是否需要登录**：是

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单 ID |

**请求参数**：无

> 仅「已支付」状态（status=2）的订单可申请退款，申请后状态变为「退款中」。

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

### 4.9 确认退款

`PUT /voucher-order/{id}/refund/confirm`

**是否需要登录**：是

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单 ID |

**请求参数**：无

> 仅「退款中」状态（status=5）的订单可确认退款，确认后状态变为「已退款」。

**响应示例**

```json
{
  "success": true,
  "errorMsg": null,
  "data": null
}
```

---

## 5. 数据模型

### 5.1 User（用户）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| email | String | 邮箱 |
| password | String | 密码（加密存储，接口不返回） |
| nickName | String | 昵称 |
| createTime | DateTime | 创建时间 |
| updateTime | DateTime | 更新时间 |

### 5.2 UserDTO（用户信息返回）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户 ID |
| nickName | String | 昵称 |
| email | String | 邮箱 |

### 5.3 Voucher（优惠券）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| title | String | 标题 |
| subTitle | String | 副标题 |
| rules | String | 使用规则 |
| payValue | Long | 支付金额（单位：分） |
| actualValue | Long | 抵扣金额（单位：分） |
| type | Integer | 类型：`0`=普通券，`1`=秒杀券 |
| status | Integer | 状态：`1`=上架，`2`=下架，`3`=过期 |
| createTime | DateTime | 创建时间 |
| updateTime | DateTime | 更新时间 |

> 秒杀券（type=1）额外返回以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| stock | Integer | 库存数量 |
| beginTime | DateTime | 生效时间 |
| endTime | DateTime | 失效时间 |

### 5.4 SeckillVoucher（秒杀券扩展信息）

| 字段 | 类型 | 说明 |
|------|------|------|
| voucherId | Long | 关联优惠券 ID（主键） |
| stock | Integer | 库存 |
| beginTime | DateTime | 生效时间 |
| endTime | DateTime | 失效时间 |
| createTime | DateTime | 创建时间 |
| updateTime | DateTime | 更新时间 |

### 5.5 VoucherOrder（订单）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，雪花算法生成 |
| userId | Long | 下单用户 ID |
| voucherId | Long | 优惠券 ID |
| payType | Integer | 支付方式：`1`=余额，`2`=支付宝，`3`=微信 |
| status | Integer | 状态：`1`=未支付，`2`=已支付，`3`=已核销，`4`=已取消，`5`=退款中，`6`=已退款 |
| createTime | DateTime | 下单时间 |
| payTime | DateTime | 支付时间 |
| useTime | DateTime | 核销时间 |
| refundTime | DateTime | 退款时间 |
| updateTime | DateTime | 更新时间 |

---

## 6. 枚举值速查

### 订单状态（status）

| 值 | 含义 | 可执行操作 |
|----|------|-----------|
| 1 | 未支付 | 支付、取消 |
| 2 | 已支付 | 核销、申请退款 |
| 3 | 已核销 | - |
| 4 | 已取消 | - |
| 5 | 退款中 | 确认退款 |
| 6 | 已退款 | - |

### 支付方式（payType）

| 值 | 含义 |
|----|------|
| 1 | 余额支付 |
| 2 | 支付宝 |
| 3 | 微信 |

### 优惠券类型（type）

| 值 | 含义 |
|----|------|
| 0 | 普通券 |
| 1 | 秒杀券 |

### 优惠券状态（status）

| 值 | 含义 |
|----|------|
| 1 | 上架 |
| 2 | 下架 |
| 3 | 过期 |
