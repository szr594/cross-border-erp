# 订单模块数据库设计

本设计为干净的 Java 版 MySQL 8.4 模型，不迁移原公司数据库或真实业务数据。主键使用 `BIGINT AUTO_INCREMENT`，时间使用 UTC `DATETIME(6)`，实体时间类型使用 `Instant`。字符集使用 `utf8mb4`。

## 表关系

```text
sales_channels 1---n sales_stores 1---n sales_orders 1---n sales_order_items
                                      |                   |
                                      |                   n
                                      |                   |
                                      |        order_procurement_requests
                                      n
                              order_status_events
```

采购分配、库存分配、包裹和打包记录由各自模块建表并引用订单或订单商品，不在订单表复制数量事实。

## `sales_channels`

| 列 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `code` | VARCHAR(32) | 唯一；`DEMO`、`CSV`，预留 `FAIRE` |
| `name` | VARCHAR(100) | 非空 |
| `enabled` | BOOLEAN | 非空，默认 true |
| `created_at` | DATETIME(6) | 非空，UTC |

## `sales_stores`

| 列 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `channel_id` | BIGINT | 外键 `sales_channels.id` |
| `external_store_id` | VARCHAR(128) | 非空 |
| `code` | VARCHAR(64) | 唯一，CSV 使用的稳定店铺编码 |
| `name` | VARCHAR(200) | 非空 |
| `current_owner_id` | BIGINT | 可空，外键 `app_users.id` |
| `status` | VARCHAR(32) | `ACTIVE`/`INACTIVE` |
| `created_at` | DATETIME(6) | 非空，UTC |
| `updated_at` | DATETIME(6) | 非空，UTC |

唯一约束：`(channel_id, external_store_id)`。

## `sales_orders`

| 列 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `order_no` | VARCHAR(32) | 唯一 ERP 编号 |
| `store_id` | BIGINT | 非空，外键 `sales_stores.id` |
| `external_order_id` | VARCHAR(128) | 非空 |
| `display_order_no` | VARCHAR(128) | 页面展示编号 |
| `external_status` | VARCHAR(64) | 标准化外部状态 |
| `external_status_raw` | VARCHAR(128) | 原始状态文本 |
| `fulfillment_exception` | BOOLEAN | 非空，默认 false |
| `exception_reason` | VARCHAR(500) | 当前未解决异常原因 |
| `responsible_user_id` | BIGINT | 负责人快照，外键 `app_users.id` |
| `currency` | CHAR(3) | ISO 4217 |
| `ordered_at` | DATETIME(6) | 下单时间，UTC |
| `ship_after` | DATETIME(6) | 最早发货时间，UTC |
| `requested_ship_at` | DATETIME(6) | 要求发货时间，UTC |
| `external_updated_at` | DATETIME(6) | 外部更新时间，UTC |
| `customer_id` | VARCHAR(128) | 外部客户 ID |
| `customer_name` | VARCHAR(200) | 客户名称快照 |
| `recipient_name` | VARCHAR(200) | 收件人 |
| `recipient_phone` | VARCHAR(64) | 敏感字段 |
| `country_code` | CHAR(2) | ISO 3166-1 alpha-2 |
| `province` | VARCHAR(128) | 敏感字段 |
| `city` | VARCHAR(128) | 敏感字段 |
| `district` | VARCHAR(128) | 敏感字段 |
| `address_line1` | VARCHAR(500) | 敏感字段 |
| `address_line2` | VARCHAR(500) | 敏感字段 |
| `postal_code` | VARCHAR(32) | 敏感字段 |
| `note` | VARCHAR(1000) | 订单备注 |
| `source_payload` | JSON | 可空；只允许脱敏业务载荷 |
| `version` | BIGINT | 乐观锁，非空 |
| `created_at` | DATETIME(6) | 非空，UTC |
| `updated_at` | DATETIME(6) | 非空，UTC |

唯一约束：`(store_id, external_order_id)`。索引覆盖 `store_id`、`responsible_user_id`、`external_status`、`ordered_at`、`requested_ship_at`、`customer_id`。

`source_payload` 不得保存凭据、令牌或可绕过角色字段裁剪的未脱敏完整响应。

## `sales_order_items`

| 列 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `order_id` | BIGINT | 非空，外键 `sales_orders.id` |
| `external_item_id` | VARCHAR(128) | 非空 |
| `external_product_id` | VARCHAR(128) | 可空 |
| `external_variant_id` | VARCHAR(128) | 可空 |
| `external_sku` | VARCHAR(128) | 可空 |
| `erp_sku_id` | BIGINT | 可空，后续外键 `erp_skus.id` |
| `product_name` | VARCHAR(500) | 非空快照 |
| `variant_name` | VARCHAR(500) | 可空快照 |
| `image_url` | VARCHAR(1000) | 只允许演示资源 |
| `quantity` | INTEGER | 非空，检查 `quantity > 0` |
| `unit_price_minor` | BIGINT | 可空，最小货币单位 |
| `currency` | CHAR(3) | 可空 |
| `fulfillment_status` | VARCHAR(32) | `NORMAL`/`CANCELED`/`RETURNED` |
| `note` | VARCHAR(500) | 可空 |
| `version` | BIGINT | 乐观锁 |
| `created_at` | DATETIME(6) | 非空，UTC |
| `updated_at` | DATETIME(6) | 非空，UTC |

唯一约束：`(order_id, external_item_id)`。商品名称、规格和价格必须保留订单快照。

## `order_procurement_requests`

| 列 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `order_item_id` | BIGINT | 非空，外键 `sales_order_items.id` |
| `requested_quantity` | INTEGER | 非空且大于 0 |
| `status` | VARCHAR(32) | `REQUESTED`/`CONVERTED`/`WITHDRAWN` |
| `requested_by` | BIGINT | 非空，外键 `app_users.id` |
| `requested_at` | DATETIME(6) | 非空，UTC |
| `withdrawn_by` | BIGINT | 可空，外键 `app_users.id` |
| `withdrawn_at` | DATETIME(6) | 可空，UTC |
| `note` | VARCHAR(500) | 可空 |
| `version` | BIGINT | 乐观锁 |

状态为 `REQUESTED` 的有效申请总量不得超过服务层事务内重新计算的商品实时缺口。

## `order_status_events`

| 列 | 类型 | 约束/说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键 |
| `order_id` | BIGINT | 非空，外键 `sales_orders.id` |
| `event_type` | VARCHAR(64) | 非空 |
| `from_value` | VARCHAR(128) | 可空 |
| `to_value` | VARCHAR(128) | 可空 |
| `operator_id` | BIGINT | 系统事件可空，外键 `app_users.id` |
| `source` | VARCHAR(32) | `USER`/`IMPORT`/`SYSTEM` |
| `remark` | VARCHAR(500) | 可空 |
| `occurred_at` | DATETIME(6) | 非空，UTC |

事件至少覆盖订单导入、负责人变更、采购申请、采购撤回、取消、异常创建和异常解决。

## 删除和并发规则

- 订单、订单商品和事件不提供物理删除接口。
- 取消订单使用业务状态和事件表达。
- 所有修改订单或采购申请的命令携带版本号并使用乐观锁。
- 外键默认使用 `RESTRICT`，禁止级联删除业务事实。
