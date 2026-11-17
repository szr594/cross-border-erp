# 旧业务订单投影替换与虚拟机重启验收方案

2026-11-17，设计已获用户确认。关联替换实现及本地验收见 [验收记录](procurement-projection-acceptance.md)；本轮未执行虚拟机升级或重启。

## 实施前缺口（历史）

order_event_projections 只有订单 ID、版本和商品 ID/数量 JSON。PackingTask、PackingBoxItem、InventoryAllocation 仍通过 JPA 关联 SalesOrder/SalesOrderItem；采购需求也是订单所有者的数据。不能把消息投影验收等同于旧业务依赖已替换。
虚拟机已启动 Order/RabbitMQ 影子实例，但尚未部署本文的新关联版本。此前的重启记录不能代替新版本消息及业务数据恢复验收。

## 拟实施的数据变更

旧 backend 新增本地 order_business_projections、order_item_business_projections，通过 Flyway V10 建表；已有 order_event_projections 保留兼容，不修改已执行 V9。
订单投影保存 orderId、orderNo、外部状态、异常标志、负责人 ID/显示名、sourceVersion。商品投影保存 orderItemId、orderId、SKU、商品名/规格、采购与库存匹配所需业务键、数量、商品履约状态、sourceVersion。
不包含客户电话、完整地址、原始第三方 JSON、密码和令牌。投影 ID 均来自 Order，不在旧后端重新生成。
创建新事件 schema（order.created.v2 / order.updated.v2），保留 v1 消费兼容。新消费者严格校验所有字段，Inbox、订单投影和商品投影一个事务提交；同聚合旧版本不得覆盖新版本。商品集合替换不得误删库存和历史打包引用。

## 业务关联改造

先将原数据回填到新投影，核对数量、ID 和业务字段，再以开关切换读路径。库存分配与打包改为关联本地商品/订单投影，历史记录可追溯；新增外键前检查悬空引用并停止迁移，不自动删除异常数据。
采购需求认领/撤回由 Order 权威接口处理，旧采购不能继续写原需求表。收货仅通过采购关联获取订单商品投影，不跨库查询。
分配、采购转单和打包等命令需要对订单状态进行所有者授权/冻结校验，不能仅凭最终一致投影认定订单可处理。同步取消使用已批准的释放幂等结果，必须与分配/打包串行互斥。
只有所有旧业务写入方完成桥接，才能关闭旧订单命令并统一迁移写路由。不采用新旧两套权威订单双写。

## 重启验收

先完成本地新关联、已有业务闭环、同步取消及数据迁移回退测试，再部署包含 Order/RabbitMQ 的新版本。
目标虚拟机检查：docker/systemd 开机启动、容器 restart 策略、数据库和 Broker 持久卷、只读密钥权限、net.ipv4.ip_forward 持久配置、磁盘与内存。备份运行配置及数据库后，由操作者执行计划重启。
重启前记录订单 ID、库存分配数量、打包状态、Outbox/Inbox 计数和未处理事件 ID；重启后核对自动启动、原账号登录、原业务记录、积压事件恢复及去重。
只有启动成功不能代替数据一致性验收。虚拟机重启与业务投影切换分别记录证据，失败时回退到维护窗口前配置，禁止用旧整库覆盖新写入。
