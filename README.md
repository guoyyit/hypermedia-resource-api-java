# Hypermedia Order API

一套基于 **HTTP 超媒体控制** 的资源访问接口（Spring Boot 3 / Java 17）。
客户端不依赖预先硬编码的路径，而是**只依据响应中返回的链接与可执行字段（controls）**
推进资源状态：

* 每个资源都有 `self` 链接和当前状态下可用的转移链接；
* 转移链接只在状态与访问上下文允许时出现，失效/非法转移永远不会被返回；
* 列表具备稳定排序与分页导航；
* 写入使用 `ETag` / `If-Match` / `If-None-Match` 做条件读取与条件更新；
* 支持两种语义等价的表示形式及内容协商；
* 错误响应本身也是可导航的超媒体资源。

---

## 1. 运行与测试

```bash
mvn spring-boot:run          # http://localhost:8080
mvn test                     # 53 个自动化测试
```

启动时默认写入 6 个演示订单 `O-1001..O-1006`，分别覆盖全部状态；
可用配置项 `hypermedia.data.seed-on-startup=false` 关闭。

入口：

```bash
curl -s http://localhost:8080/ -H 'Accept: application/hal+json'
```

---

## 2. 资源模型与状态机

### 2.1 订单（Order）

属性（规范输出顺序）：

| 字段 | 说明 |
| --- | --- |
| `id` | 订单 id，如 `O-1001` |
| `status` | 生命周期状态（见下） |
| `customerName` | 客户名（仅 DRAFT 可改） |
| `totalPrice` | 由行项目计算（`sum(quantity*unitPrice)`），只读 |
| `itemCount` | 行项目数，只读 |
| `updatedAt` | 最近一次成功提交的时间（ISO-8601，UTC） |

订单不直接暴露内部并发版本号；版本通过弱 `ETag`（`W/"O-1001:3"`）表达。

### 2.2 行项目（OrderItem）

`id`（形如 `O-1001-I001`，按订单内位置确定性分配）、`sku`、`name`、
`quantity`、`unitPrice`。行项目持有对所属订单的引用 `order`，
从而构成 `order → items → order` 的图，用于演示循环安全嵌入。

### 2.3 状态机（链接可见性的唯一事实来源）

```
DRAFT ──submit──▶ PENDING_PAYMENT ──pay──▶ PAID ──ship──▶ SHIPPED ──deliver──▶ DELIVERED
  │                    │
  └──────cancel───────▶ CANCELLED ◀────cancel────┘
```

| 当前状态 | 出现的转移控制 |
| --- | --- |
| `DRAFT` | `edit`(PUT), `submit`(POST), `cancel`(POST) |
| `PENDING_PAYMENT` | `pay`, `cancel` |
| `PAID` | `ship` |
| `SHIPPED` | `deliver` |
| `DELIVERED` / `CANCELLED` | 无（终态，只剩 `self`/`items`/`orders` 安全链接） |

`DELIVERED`、`CANCELLED` 不返回任何转移链接；非法动作即使被直接调用也会被拒绝
（链接可见性与执行校验共用同一张状态表，二者不会不一致）。

---

## 3. 导航语义（链接与 controls）

链接对象示例：

```json
"submit": { "href": "/orders/O-1001/transitions/submit", "method": "POST",
            "title": "Submit order for payment" }
```

* 安全（GET）链接只含 `href`（URI 模板时含 `"templated": true`），**不含** `method`；
* 非 GET 控制带 `method`（`POST`/`PUT`）提示客户端如何执行；
* 链接始终按 `rel` 字典序输出，保证字节级确定性；
* 标准关系：`self`、`orders`、`items`、`order`、`first`、`last`、`prev`、`next`、`create`、`home`、`retry`。

### 端点

| 方法与路径 | 说明 |
| --- | --- |
| `GET  /` | API 入口，通告 `orders` 与 `create` |
| `GET  /orders` | 分页/排序集合（见 §5） |
| `POST /orders` | 创建订单，返回 `201` + `Location` + ETag |
| `GET  /orders/{id}` | 订单表示，支持条件读取 |
| `PUT  /orders/{id}` | 仅 DRAFT 可改；**必须**带 `If-Match` |
| `POST /orders/{id}/transitions/{action}` | 执行状态转移；**必须**带 `If-Match` |
| `GET  /orders/{id}/items` | 行项目集合 |
| `GET  /orders/{id}/items/{itemId}` | 单个行项目 |

不支持的方法返回 `405` 的可导航错误；未知路径返回 `404` 的可导航错误。

---

## 4. 按需嵌入、字段选择、循环与截断（确定性约定）

### 4.1 参数

| 参数 | 说明 |
| --- | --- |
| `embed` | 逗号分隔的点路径，去重后保留请求顺序。订单根合法路径形如 `items.order.items...`；行项目根以 `order...` 起始 |
| `depth` | 自动展开深度，非负整数，默认 `1`；**始终受配置上限 `hypermedia.embedding.max-depth`（默认 3）封顶** |
| `fields` | 逗号分隔字段，按类型的规范顺序输出；未知字段返回 `400 INVALID_EMBED` |

`depth` 控制“自动展开”，`embed` 控制“显式路径”，二者取并集。

### 4.2 输出顺序（确定性）

* 属性：固定规范顺序（见 §2），与请求字段顺序无关；
* 链接：按 `rel` 字典序；
* 嵌入关系：按关系名字典序；
* `items` 列表成员：按行项目 `id` 升序；
* `truncatedEmbeds`：按首次遇到的顺序、去重输出。

### 4.3 循环安全与截断

展开过程中为每个节点维护“当前展开链（chain）”与“已封口（sealed）”集合：

1. `order → items → order` 的**第一次回边允许嵌入**所属订单（这是常见的取“父资源”需求）；
2. 当一个节点的出边已经展开过一次（sealed），再次到达时只渲染链接、不再展开；
3. 再次回到链上同一节点（真正的环）时同样只保留链接。

因此对 `order → items → order → items → …` 的循环**保证终止**，绝不无限展开。
被截断的路径会出现在 `_meta.truncatedEmbeds`（VND 布局为 `meta.truncatedEmbeds`），例如：

```bash
curl '/orders/O-1001?depth=10'
# _meta.truncatedEmbeds = ["items.order.items"]
```

* 仅当展开是被 `depth` 上限裁断，或命中循环时才记录截断；
* 显式 `embed` 路径已被完整满足时**不**记录截断；
* 显式嵌入空集合返回空数组，不视为截断；
* 非法 `embed` 路径/关系、非法 `depth`、未知 `fields` 一律 `400` 并给出错误契约。

---

## 5. 列表：稳定排序与分页

`GET /orders` 参数：

| 参数 | 默认 | 规则 |
| --- | --- | --- |
| `page` | `1` | 1 基页码，`>=1`；超过 `max-page`（>0 时）返回 400 |
| `size` | `hypermedia.pagination.default-size`（20） | `1..max-size`（默认上限 100），越界 400 |
| `sort` | `id` | 允许 `id`、`customerName`、`status`、`updatedAt`，前缀 `-` 降序；未知键 400 |

所有比较器都以 `id` 作为最终 tie-breaker，因此排序在重复键、翻页和重复请求之间**稳定**。

导航链接：

* 始终有 `self`、`first`、`last`、`create`；
* `prev` 仅在 `page>1` 出现；`next` 仅在 `page<lastPage` 出现；
* 导航 href 保留当前 `size` 与 `sort`。

### 空页 vs 越界页（可区分、已文档化）

| 情况 | HTTP | `pageState` | `empty` | 成员 | 链接 |
| --- | --- | --- | --- | --- | --- |
| 集合为空且请求第 1 页 | `200` | `EMPTY` | `true` | `[]` | self/first/last/create，无 prev/next |
| 页码超过末页 | `404` | `OUT_OF_RANGE` | `false` | `[]` | 仅安全导航 self/first/last/create（**不**给 prev/next） |
| 正常页 | `200` | `CONTENT` | `false` | 当前页成员 | 含可用的 prev/next |

集合还返回 `page`、`size`、`totalItems`。

---

## 6. 条件读取与条件更新（ETag / 并发 / 原子性）

每个订单与行项目响应都带弱 `ETag`：`W/"<orderId>:<version>"`。
任何成功写入都把 `version` 加一并刷新 `updatedAt`。

| 头 | 行为 |
| --- | --- |
| `GET` + `If-None-Match: <etag>` | 命中返回 `304`（无响应体），未命中返回 `200` |
| `PUT`/转移 + `If-Match` | 与当前版本一致才执行；不一致 `412 PRECONDITION_FAILED` |
| 写操作缺少 `If-Match` | `428 PRECONDITION_REQUIRED`（错误体带 `retry` 链接） |
| `PUT` + `If-None-Match: *` | 资源已存在时 `412` |

### 并发与“重复转移不改两次状态”

* 存储对每个聚合 id 使用独立监视器；读返回深拷贝，写在锁内完成；
* 写入先在**分离的副本**上执行业务变更，成功后才原子替换；
  业务校验或版本检查抛错时，原聚合不变，**不留下中间状态**；
* 版本在锁内比较，版本不符直接失败。多个客户端用同一 ETag 并发提交，
  恰好一个成功，其余得到 `412`（自动化测试用 8/6 线程验证）；
* 转移在锁内**重新依据当前状态**校验：
  * 用旧 ETag 重放 → `412 VERSION_CONFLICT`；
  * 用新 ETag 重复同一动作 → 该控制已不存在 → `409 ILLEGAL_TRANSITION`，状态不变；
* 非 DRAFT 订单执行字段更新 → `409 UPDATE_NOT_ALLOWED`。

---

## 7. 内容协商

`Accept` 头按 RFC 7231（q 值 → 具体性 → 出现顺序）选择：

| 媒体类型 | 布局 |
| --- | --- |
| `application/hal+json`（默认；`application/json` 也映射到它） | 顶层属性 + `_links` / `_embedded` / `_meta` |
| `application/vnd.hypermedia+json` | `data` / `links` / `embedded` / `meta` |
| 无 `Accept` / 空 / `*/*` / `application/*` | 使用配置默认（HAL） |

两种布局**仅字段摆放/命名不同，属性值、可用链接、资源状态与语义完全一致**；
响应带 `Vary: Accept`。

偏好无法满足时返回 `406`（即使 406 也以默认 HAL 输出，保证错误可读）：

```json
{ "code": "NEGOTIATION_FAILED",
  "detail": "No supported representation matches the Accept header; offered: application/hal+json, application/vnd.hypermedia+json",
  "_links": { "self": {...}, "orders": {...} } }
```

明确给出支持的类型与可继续导航的链接；不同表示之间不会改变资源状态。

---

## 8. 错误契约（本身可导航）

所有错误（含 404/405/406/400/409/412/428/500）都返回超媒体表示：

| 字段 | 说明 |
| --- | --- |
| `type` | 稳定的 problem 类型 URI |
| `status` | HTTP 状态码 |
| `code` | 稳定机器码（见下表） |
| `detail` | 面向客户端、安全的信息（**绝不**包含堆栈、内部类名） |
| `timestamp` | ISO-8601 生成时间 |
| `_links` / `links` | `self`，以及适用的 `retry`、`orders`、`home`、`first` 等安全链接 |

| HTTP | code |
| --- | --- |
| 400 | `VALIDATION_ERROR` / `MALFORMED_REQUEST` / `INVALID_QUERY_PARAMETER` / `INVALID_SORT` / `INVALID_EMBED` / `UNKNOWN_ACTION` |
| 404 | `RESOURCE_NOT_FOUND` / `ORDER_NOT_FOUND` / `ITEM_NOT_FOUND` / 集合越界 `pageState=OUT_OF_RANGE` |
| 405 | `METHOD_NOT_ALLOWED` |
| 406 | `NEGOTIATION_FAILED` |
| 409 | `ILLEGAL_TRANSITION` / `UPDATE_NOT_ALLOWED` |
| 412 | `PRECONDITION_FAILED` / `VERSION_CONFLICT` |
| 428 | `PRECONDITION_REQUIRED` |
| 500 | `INTERNAL_ERROR`（泛化信息，不泄露内部细节） |

---

## 9. 配置项

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `hypermedia.pagination.default-size` | `20` | 缺省每页数量 |
| `hypermedia.pagination.max-size` | `100` | 每页数量上限 |
| `hypermedia.pagination.max-page` | `0` | 最大页码；`0` 表示不限制 |
| `hypermedia.embedding.max-depth` | `3` | 嵌入深度硬上限，始终优先生效 |
| `hypermedia.representation.default-media-type` | `application/hal+json` | 无可用 Accept 时的默认表示 |
| `hypermedia.data.seed-on-startup` | `true` | 启动时是否写入演示数据 |

`application.properties` 同时设置：

* `spring.mvc.throw-exception-if-no-handler-found=true`
* `spring.web.resources.add-mappings=false`

使未知路径抛出可被全局 advice 渲染为可导航错误的异常。

---

## 10. 自动化测试覆盖

| 测试类 | 覆盖点 |
| --- | --- |
| `LinkVisibilityApiTest` | 各状态链接可见性、终态无控制、控件方法提示、入口链接 |
| `EmbeddingApiTest` | depth=0/默认/显式路径、字段投影、非法路径/字段、循环终止、截断确定性、深度上限 |
| `RepresentationRendererTest` | 链接/属性排序、两种布局等价、重复渲染稳定 |
| `PaginationApiTest` | first/last/prev/next、稳定排序、越界页安全链接、非法分页/排序、参数保留 |
| `EmptyCollectionApiTest` | 空集合 `EMPTY/200` 与越界 `OUT_OF_RANGE/404` 的区分 |
| `ConditionalConcurrencyApiTest` | 304、428、412、ETag 递增、重复转移幂等、失败不留中间状态、全生命周期 |
| `ConcurrencyThreadApiTest` | 8 路并发更新 / 6 路并发转移：恰好一次成功，其余冲突契约 |
| `ContentNegotiationApiTest` | HAL/VND 语义等价、q 值、406 契约、404/405/400 错误导航与不泄露内部细节 |

---

## 11. 设计要点速览

* **一致性**：链接可见性、转移执行共用 `OrderStatus` 状态表；
* **并发**：每聚合监视器 + 乐观版本号 + 副本提交，失败无中间状态；
* **幂等**：状态转移天然一次性（状态机门控）+ 版本号双重保护；
* **确定性**：所有 Map 使用有序结构，列表有显式比较器与 tie-breaker；
* **可导航**：正常与错误响应都带 `self` 和安全/重试链接；
* **不泄露**：错误信息面向客户端，内部异常仅返回泛化契约。
