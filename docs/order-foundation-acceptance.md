# MS-08 第一批：独立数据层与验签

日期：2026-11-17。第一批不发布订单写路由。

已实现 services/order-service 独立入口、数据库环境变量、JPA 实体与 Repository、三个 Flyway 版本（V1 用户投影、V2 订单、V8 CSV 记录）。编号沿用源业务版本便于对照，不引用旧库 Flyway 历史。
订单、店铺、采购需求、事件及 CSV 记录使用本服务本地关联；没有依赖库存、打包或认证服务的实体、Repository 或数据库连接。
user_display_projections 仅包含 id、login_name、display_name、status、source_version，没有密码、刷新会话或密钥。该投影的数据导入/事件更新机制留到 RabbitMQ 兼容桥接阶段。
RS256 验签只使用外部公钥，检查至少 2048 位并校验 issuer/有效期；不支持 HS256 回退。

实际验证：`mvn -q -pl services/order-service test`，4 项通过，无跳过。测试使用 H2 MySQL 模式，覆盖 Flyway/JPA、用户投影列白名单、列表 DTO 序列化结构、匿名/无效令牌 401、正确 issuer 与错误 issuer 验签。

第一批契约测试只覆盖 DTO 和认证错误，不提供虚假的业务控制器，也不声称完整 HTTP 业务接口已完成。查询、命令、CSV 预检/确认、跨服务履约投影和生产数据迁移后续实施。真实 MySQL 8.4 迁移与外键约束仍需单独验证。

未改前端、网关路由和虚拟机部署。MS-08 继续保持未完成。
