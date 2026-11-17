# 订单服务 MySQL 与 CSV 接口进度

2026-11-17，隔离 MySQL 8.4 容器，独立 order_app 账号，端口仅绑定本机 13318。未连接虚拟机。

## 已实际验证

- Flyway V1/V2/V8 创建用户投影、订单及导入记录，JPA validate 成功。
- 本服务新建基线将源代码的行内 REFERENCES 改为显式表级外键，确保 MySQL 真正建立本地外键；未修改旧 backend 历史迁移。
- CSV 控制器、DTO 和应用服务已独立迁移，用户查询改为本地显示投影，不包含跨库连接。
- 真实 HTTP 测试：OWNER 携带 RS256 令牌 multipart 预检返回 201/VALIDATED，错误分页 200/0 条，确认返回 200/COMPLETED，创建一张订单。
- 用户、店铺测试数据及确认操作由测试事务回滚，防止污染后续测试。元数据列检查限定当前 schema。

实际命令（测试凭据仅用于隔离本地容器）：

```powershell
$env:ORDER_TEST_DATABASE_URL='jdbc:mysql://127.0.0.1:13318/cross_erp_order_it2?connectionTimeZone=UTC'
$env:ORDER_TEST_DATABASE_USER='order_app'
$env:ORDER_TEST_DATABASE_PASSWORD='<本地测试密码>'
mvn -q -pl services/order-service test
```

结果：5 项通过，无失败、错误或跳过。

## 未完成

订单列表/详情/履约/事件等完整业务接口、CSV 并发确认与失败回滚专项、多角色权限、Outbox 与用户投影同步、旧业务兼容桥接、既有数据迁移对账、容器切换和回退尚需继续实施。CSV 目前不写 Outbox，禁止对外切换写路由。现有测试不能证明跨服务整体流程已完成。
