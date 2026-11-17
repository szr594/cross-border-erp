# 订单消息集成验收

日期：2026-11-17。真实 RabbitMQ 3.13 和 MySQL 8.4，仅本机隔离容器。未启用虚拟机消息。

## 实现

- 旧 backend 新增 OrderEventListener、事务性 OrderEventConsumer 和持久交换机/队列拓扑。
- Flyway V9 创建 order_event_inbox 和 order_event_projections，事件消费不直接改写旧 sales_orders，避免双写权威数据。该投影尚未替代旧业务实体关联。
- Inbox 唯一键 consumer_name/event_id 与投影更新在一个本地事务；重复投递直接结束。投影只接受更大的 source_version，旧事件不覆盖新值。
- 监听器由 Spring 容器在处理成功后 ACK；异常配置最多三次重试，200ms 起指数退避，耗尽后拒绝且不重新入原队列，由 DLX 投递 legacy.order-sync.dead。
- 默认 app.events.enabled 未开启，现有生产版本不会自动连接消息消费者。

## 实际命令与结果

```text
mvn -q -pl backend -Dtest=OrderMessagingIT test
mvn -q -pl services/order-service -Dtest=OrderPublishingIT test
```

均退出 0。消费者 2 项测试验证：重复/乱序不会覆盖投影；非法负载实际调用三次后进入死信，且 Inbox 不留下成功记录。
发布器 1 项测试验证：MySQL Outbox 实际消息到达 RabbitMQ 持久队列，messageId 和 body 一致，confirm 成功后 published_at 非空。

这是发布端与消费端分别运行的真实中间件集成测试，不是 CSV→发布→消费同时在线的全链路测试。尚未覆盖 Broker 故障恢复、确认丢失、ACK 丢失、服务进程重启、失败行事务回滚和多实例并发重复消息。死信重放工具尚未实现，当前只保留可供审查的死信，不自动无限重放。

RabbitMQ 测试用户及数据库密码仅用于隔离测试，不得复制为生产凭据。上线需单独凭据和网络配置。MS-08 未完成，生产订单写路由不切换。

## 同时在线与故障恢复补验

2026-11-17，构建独立 Order JAR 后执行：

```text
mvn -q -pl services/order-service -DskipTests package
mvn -q -pl backend -Dtest=OrderMessagingIT test
```

最终结果：5 项通过，0 失败、0 错误、0 跳过。保留了原有重复/乱序及三次重试死信测试，增加以下实际场景：

- CSV 全链路：测试启动独立 Order JVM（19082），旧 backend 消费者保持在线；以 RS256 令牌真实 multipart 预检、确认 CSV，查询源 Outbox published_at 和目标订单投影验证消息确已发布及消费。
- Broker 中断与订单进程恢复：docker stop 隔离 Broker 后确认第二批 CSV，HTTP 返回成功且 Outbox 未发布；强制终止 Order 进程，恢复 Broker 后重启同一 JAR，验证原积压事件自动发布、目标投影出现且 Inbox 唯一。
- ACK 丢失：暂停自动监听器，从真实队列以未 ACK 模式取消息，执行消费者事务并确认 Inbox 已提交，随后 abort 底层 AMQP channel；恢复监听器，验证同一负载至少再次交付，Inbox/投影仍各一条。
- Broker 持久队列恢复：暂停监听器并发送持久消息，真实停止/启动 Broker 容器，恢复监听器后消费成功。

注入方式修正记录：Spring 的 channel.close() 会归还缓存，不能证明断连，首次相关测试失败；改为 target channel.abort() 后通过，未放宽断言。

边界：消费者恢复覆盖监听器停止/启动和连接自动恢复；强制进程重启覆盖 Order JVM，不宣称旧 backend 进程或虚拟机重启已验收。测试投影仍为独立 order_event_projections，未替换采购/库存/打包原业务关联。因此这些通过不等于可以立即切换生产订单写路由。
