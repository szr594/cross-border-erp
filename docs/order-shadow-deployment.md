# 订单与 RabbitMQ 影子部署

2026-11-17。使用 compose.production.yml、compose.identity.yml、compose.order.yml 叠加。
订单独立数据库、Broker 和订单服务启动，但不修改网关路由，也不启用旧 backend 的消息消费者。现有网站仍使用旧订单业务。
RabbitMQ 的凭据在独立 Broker 上供本项目服务使用，不使用 guest；管理页面及 AMQP 端口均不对宿主机发布。
Broker 有固定 hostname 和持久卷，避免容器重建后改变节点名而无法读取原数据。

## 虚拟机操作

先检查 free -h 与 df -h，新增 MySQL、RabbitMQ、Java 服务会增加资源占用。原 4GB 虚拟机建议计划停机扩容到 8GB 后再运行全部十个容器；未扩容时先评估余量，不能保证不会 OOM。
备份现有配置，导入升级镜像，上传 compose.order.yml。保留当前 .env.production，只追加尚不存在的新变量，密码使用 openssl rand -hex 24。
公钥沿用现有 IDENTITY_KEYS_DIR/public.pem，确保只读挂载可读。

```bash
docker compose --env-file .env.production -f compose.production.yml -f compose.identity.yml -f compose.order.yml config --quiet
docker compose --env-file .env.production -f compose.production.yml -f compose.identity.yml -f compose.order.yml up -d --no-build --pull never order
docker compose --env-file .env.production -f compose.production.yml -f compose.identity.yml -f compose.order.yml ps -a
```

只启动 order 会自动启动其 order-mysql 和 rabbitmq 依赖，不重建现有前端、网关和 backend。
不要把现有订单表直接导入后开放写路由；用户显示投影、初始订单快照、跨服务命令和消费端兼容尚未部署。
影子阶段没有测试导入的订单时 Outbox 为空属于正常情况。新 Broker 未启动旧 backend 消费者，消息可能保留在队列，不宣称虚拟机发布消费闭环已完成。
回退影子部署只需 stop order rabbitmq order-mysql，保留卷；不要 down -v，也不要停止整个原生产栈。
