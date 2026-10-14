# 黑马商城（hmall）

基于 **Spring Cloud Alibaba** 的微服务电商项目，模拟"黑马商城"的完整业务流程，涵盖
商品搜索、购物车、用户登录、下单、支付等核心链路。

> 本项目为 Spring Cloud 微服务学习/实战项目，技术栈包含 Nacos、OpenFeign、Sentinel、
> Seata、RabbitMQ、Elasticsearch 等组件。

---

## 一、项目概览

### 1.1 整体架构

```
                        ┌──────────────────────────┐
                        │        Nginx (80)         │
                        │  18080 门户 / 18081 后台   │
                        └─────────────┬────────────┘
                                      │ /api
                                      ▼
                        ┌──────────────────────────┐
                        │      hm-gateway:8080      │  统一入口 + 登录鉴权
                        │   (Spring Cloud Gateway)  │
                        └─────────────┬────────────┘
                                      │ lb:// 负载均衡
        ┌───────────────┬─────────────┼─────────────┬───────────────┐
        ▼               ▼             ▼             ▼               ▼
  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐
  │item-service│  │cart-service│  │user-service│  │trade-service│ │pay-service│
  │   :8081   │  │   :8082   │  │   :8084   │  │   :8085    │  │   :8086   │
  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘
        │              │              │              │              │
        ▼              ▼              ▼              ▼              ▼
     hm-item       hm-cart        hm-user        hm-trade       hm-pay
                      └──── OpenFeign 远程调用（hm-api）────┘
```

### 1.2 模块说明

| 模块 | 端口 | 说明 |
|------|------|------|
| `hm-gateway` | 8080 | 网关服务，统一请求入口，负责路由转发与 JWT 登录鉴权 |
| `item-service` | 8081 | 商品服务，商品管理 + Elasticsearch 搜索 |
| `cart-service` | 8082 | 购物车服务 |
| `user-service` | 8084 | 用户服务，登录认证、地址管理 |
| `trade-service` | 8085 | 交易服务，下单、订单管理、延迟消息关单 |
| `pay-service` | 8086 | 支付服务，支付单管理、余额支付 |
| `hm-common` | — | 公共模块：统一返回体、异常处理、工具类、拦截器、MQ 配置 |
| `hm-api` | — | 远程调用模块：Feign 客户端接口 + DTO + 降级工厂 |
| `hm-service` | 8080 | 单体版本（早期阶段产物，微服务拆分前的形态） |
| `hmall-nginx` | 80/18080/18081/18082 | Nginx 及前端静态资源（门户 / 后台 / 刷新后台） |

---

## 二、技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 2.7.12 |
| 微服务 | Spring Cloud | 2021.0.3 |
| 微服务 | Spring Cloud Alibaba | 2021.0.4.0 |
| JDK | Java | 11 |
| 注册中心 / 配置中心 | Nacos | — |
| 服务调用 | OpenFeign + OkHttp（连接池） | — |
| 服务保护 | Sentinel | — |
| 分布式事务 | Seata | — |
| 网关 | Spring Cloud Gateway | — |
| 持久层 | MyBatis-Plus | 3.5.3.2 |
| 数据库 | MySQL | 8.0.23 |
| 搜索引擎 | Elasticsearch | 7.12.1 |
| 消息队列 | RabbitMQ | — |
| 工具库 | Hutool | 5.8.11 |
| 接口文档 | Knife4j / Swagger | — |

---

## 三、核心功能与实现

### 3.1 统一网关与登录鉴权

- 网关 [`AuthGlobalFilter`](file:///d:/dev/code/springcloud/hmall/hm-gateway/src/main/java/com/hmall/gateway/filter/AuthGlobalFilter.java)
  作为全局过滤器，拦截**所有请求**，对非放行路径校验请求头中的 `authorization`（JWT）。
- 校验通过后，从 Token 解析出 `userId`，写入请求头 `user-info` 后转发到下游微服务。
- 需要放行的路径由 `hm.auth.excludePaths` 配置（如 `/search/**`、`/users/login`、`/items/**`）。
- 动态路由：网关路由表存储在 Nacos 配置 `gateway-routes.json` 中，由
  [`DynamicRouteLoader`](file:///d:/dev/code/springcloud/hmall/hm-gateway/src/main/java/com/hmall/gateway/route/DynamicRouteLoader.java)
  监听配置变更并实时刷新，**无需重启网关**。

### 3.2 用户身份透传

下游微服务通过
[`UserInfoInterceptor`](file:///d:/dev/code/springcloud/hmall/hm-common/src/main/java/com/hmall/common/interceptor/UserInfoInterceptor.java)
读取请求头 `user-info`，存入 `ThreadLocal`（`UserContext`），业务层即可随时 `UserContext.getUser()` 拿到当前登录用户。

- 服务内部使用 Feign 互相调用时，由
  [`DefaultFeignConfig`](file:///d:/dev/code/springcloud/hmall/hm-api/src/main/java/com/hmall/api/config/DefaultFeignConfig.java)
  中的 `RequestInterceptor` 自动携带 `user-info`，实现**身份在服务链路中的透传**。

### 3.3 商品搜索（Elasticsearch）

- `item-service` 集成 ES，`SearchController` 提供 `/search/list` 搜索接口，支持关键词、品牌、分类、价格区间过滤与分页。
- 引入 `elasticsearch-rest-high-level-client`，索引文档 `ItemDoc`。

### 3.4 下单与分布式事务

[`OrderServiceImpl.createOrder`](file:///d:/dev/code/springcloud/hmall/trade-service/src/main/java/com/hmall/trade/service/Impl/OrderServiceImpl.java#L52-L110) 是下单核心流程：

1. 通过 Feign 调用 `item-service` 查询商品、计算总价；
2. 写入订单表、订单详情表；
3. 调用 `cart-service` 清理购物车商品；
4. 调用 `item-service` 扣减库存；
5. 发送**延迟消息**，用于超时未支付自动关单。

该方法标注 `@GlobalTransactional`（Seata），保证跨服务操作的分布式一致性。

### 3.5 订单超时关闭（RabbitMQ 延迟消息）

- 下单成功后发送延迟消息到 `trade.delay.direct` 交换机，延迟 **10 秒**（示例值）投递到 `trade.delay.order.queue`。
- [`OrderDelayMessageListener`](file:///d:/dev/code/springcloud/hmall/trade-service/src/main/java/com/hmall/trade/listener/OrderDelayMessageListener.java)
  消费消息：查询订单支付状态，
  - 已支付 → 标记订单支付成功；
  - 未支付 → 取消订单并**恢复库存**。

### 3.6 支付与异步通知

- `pay-service` 通过幂等校验创建/复用支付单，余额支付成功后：
  1. 扣减用户余额（Feign 调用 `user-service`）；
  2. 更新支付单状态（乐观锁：仅允许未支付状态更新）；
  3. 发送支付成功消息到 `pay.direct` 交换机。
- [`PayStatusListener`](file:///d:/dev/code/springcloud/hmall/trade-service/src/main/java/com/hmall/trade/listener/PayStatusListener.java)
  消费该消息，将交易订单标记为已支付，实现**支付与交易服务的解耦**。

### 3.7 服务保护与降级

- `hm-api` 中的 Feign 客户端配置了降级工厂（`ItemClientFallbackFactory`、`PayClientFallback`）。
- `cart-service`、`trade-service` 等开启 Sentinel 支持（`feign.sentinel.enabled`），配合 Sentinel 控制台（`localhost:8090`）实现限流熔断。

---

## 四、本地运行

### 4.1 环境依赖

启动前需准备以下中间件（配置文件中的地址需按需修改）：

| 组件 | 说明 | 默认地址 |
|------|------|---------|
| MySQL | 业务数据库 | `mysql:3306`（见 `application-dev.yaml`） |
| Nacos | 注册中心 + 配置中心 | `192.168.100.128:8848` |
| RabbitMQ | 消息队列 | `192.168.100.128:5672`，vhost `/hmall` |
| Elasticsearch | 商品搜索 | `7.12.1` |
| Sentinel Dashboard | 服务保护控制台 | `localhost:8090` |
| Seata Server | 分布式事务 | 见 Nacos 共享配置 `shared-seata.yaml` |

**Nacos 需要预先创建的共享配置**（多数微服务 `bootstrap.yaml` 中引用）：

- `shared-jdbc.yaml`：数据库 / MyBatis 公共配置
- `shared-log.yaml`：日志公共配置
- `shared-swagger.yaml`：接口文档公共配置
- `shared-seata.yaml`：Seata 公共配置
- `gateway-routes.json`：网关动态路由表

### 4.2 数据库

各服务使用独立数据库：

| 数据库 | 所属服务 |
|--------|---------|
| `hm-item` | item-service |
| `hm-cart` | cart-service |
| `hm-user` | user-service |
| `hm-trade` | trade-service |
| `hm-pay` | pay-service |

### 4.3 启动步骤

1. 启动 MySQL、Nacos、RabbitMQ、Elasticsearch 等中间件；
2. 在 Nacos 中创建上表中的共享配置与路由配置；
3. 编译打包：

   ```bash
   mvn clean install -DskipTests
   ```

4. 按依赖顺序启动微服务（建议顺序）：

   ```
   hm-gateway → user-service → item-service → cart-service → trade-service → pay-service
   ```

5. 启动 Nginx（可选，用于前端页面）：

   ```bash
   cd hmall-nginx
   start nginx
   ```

### 4.4 访问入口

| 入口 | 地址 | 说明 |
|------|------|------|
| 门户首页 | http://localhost:18080 | 商城用户端 |
| 管理后台 | http://localhost:18081 | 商品/用户管理 |
| 刷新后台 | http://localhost:18082 | — |
| 网关 | http://localhost:8080 | 所有接口统一入口 |
| 接口文档 | http://localhost:8080/doc.html | Knife4j |

---

## 五、配置文件说明

各服务 `src/main/resources` 下：

| 文件 | 作用 |
|------|------|
| `bootstrap.yaml` | **优先加载**，配置应用名、Nacos 地址与共享配置引用 |
| `application.yaml` | 应用配置，指定激活的 profile |
| `application-dev.yaml` | 开发环境变量（如数据库账号密码），**已被 `.gitignore` 忽略** |
| `application-local.yaml` | 本地环境变量，**已被 `.gitignore` 忽略** |

> ⚠️ 由于 `application-dev.yaml` / `application-local.yaml` 未纳入版本管理，
> clone 项目后需**自行创建**。仓库根目录提供了两份模板：
>
> - [application-dev.example.yaml](file:///d:/dev/code/springcloud/hmall/application-dev.example.yaml) → 复制为 `application-dev.yaml`
> - [application-local.example.yaml](file:///d:/dev/code/springcloud/hmall/application-local.example.yaml) → 复制为 `application-local.yaml`
>
> 将模板复制到**需要启动的微服务模块**的 `src/main/resources/` 目录下并重命名（各服务内容一致），
> 再按自己的环境修改 `host` 与 `pw` 即可。

```yaml
# application-dev.yaml（容器内互联）
hm:
  db:
    host: mysql              # 本机调试改为 127.0.0.1
    pw: 123                  # 修改为你的 MySQL 密码
```

```yaml
# application-local.yaml（本地虚拟机 / Docker）
hm:
  db:
    host: 192.168.100.128    # 修改为你自己的虚拟机 IP
    pw: 123                  # 修改为 Docker 中 MySQL 的密码
```

---

## 六、JWT 密钥说明

登录鉴权依赖密钥库文件 `hmall.jks`（位于 `hm-gateway`、`hm-service`、`user-service` 的 resources 目录）。
该文件已被 `.gitignore` 忽略（`*.jks`），需要重新生成：

```bash
keytool -genkeypair -alias hmall -keyalg RSA -keypass hmall123 -keystore hmall.jks -storepass hmall123
```

生成的 `hmall.jks` 放到上述各服务的 `src/main/resources` 目录下即可（别名、密码需与配置保持一致）。

---

## 七、项目结构

```
hmall
├── hm-common          # 公共模块（统一返回、异常、工具、拦截器、MQ 配置）
├── hm-api             # Feign 客户端 + DTO + 降级工厂
├── hm-gateway         # 网关（鉴权 + 动态路由）
├── item-service       # 商品服务（含 ES 搜索）
├── cart-service       # 购物车服务
├── user-service       # 用户服务
├── trade-service      # 交易服务
├── pay-service        # 支付服务
├── hm-service         # 单体版本（历史产物）
├── hmall-nginx        # Nginx 与前端静态资源
└── pom.xml            # 聚合父 POM（依赖与版本管理）
```

---

## 八、核心接口一览

| 服务 | 接口 | 说明 |
|------|------|------|
| item | `GET /items` | 根据 id 批量查询商品 |
| item | `PUT /items/stock/deduct` | 扣减/恢复库存 |
| item | `GET /search/list` | 商品搜索 |
| cart | `POST /carts` | 添加购物车 |
| cart | `GET /carts` | 查询我的购物车 |
| cart | `DELETE /carts` | 批量删除购物车商品 |
| user | `POST /users/login` | 用户登录 |
| user | `PUT /users/money/deduct` | 扣减用户余额 |
| trade | `POST /orders` | 创建订单 |
| trade | `PUT /orders/{orderId}` | 标记订单支付成功 |
| pay | `POST /pay-orders` | 生成支付单（仅余额支付） |
| pay | `POST /pay-orders/{id}` | 基于用户余额支付 |
| pay | `GET /pay-orders` | 查询支付单列表 |
| pay | `GET /pay-orders/biz/{id}` | 按业务订单号查询支付单 |
