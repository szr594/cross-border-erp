# Identity 本地迁移与回退验收

日期：2026-11-16。使用隔离 MySQL 8.4 容器 erp-identity-it-mysql，随机源库与目标库，以及本地独立 JVM 服务。未访问虚拟机数据。

实际命令：

```powershell
mvn -q -DskipTests package
./deploy/verify-identity-cutover.ps1
```

两条命令均退出 0。验收脚本创建虚构老板账号，真实执行 Flyway 和 SQL 数据迁移，随后切换网关与旧后端配置，再恢复旧模式。

| 检查 | 结果 |
| --- | --- |
| 迁移前旧模式登录 | 200 |
| SQL 迁移用户 ID 与密码哈希 | 保持一致 |
| 迁移后原密码经网关登录 Identity | 200，用户 ID 不变 |
| RS256 查询 Identity 当前用户 | 200 |
| RS256 经网关查询旧后端订单 | 200 |
| 新模式使用旧 HS256 令牌 | 401 |
| 有效 RS256 访问旧认证控制器 | 404，控制器未注册 |
| 重复数据迁移 | 拒绝，非零退出码 |
| 回退后原账号密码登录 | 200 |
| 回退后业务查询 | 200 |
| 回退后使用 RS256 令牌 | 401 |

脚本使用维护窗口场景，没有切换后业务写入；不代表任意运行时数据回滚能力。业务查询使用空订单库，未覆盖已有订单负责人和库存操作人数据关联。角色关联通过迁移 SQL 复制并由登录响应参与授权，但多角色权限仍需专门验收。

签发与并发测试另通过 IdentityMysqlIT 和 JwtServiceTest 验证。临时密钥和日志保留于忽略的 tmp 目录，测试数据库保留供排查，进程已退出。

## 2026-11-16 补充验收

- 演练脚本先使用 dev 初始化虚构订单、采购、收货、库存和打包数据，再切换回 prod 模式迁移。订单详情的 responsibleUser 在迁移前后保持一致，迁移后库存列表查询 200，回退后业务查询 200。
- Identity Dockerfile 构建成功。compose.identity.yml 增加独立 MySQL 实例、专用数据卷、identity_app 账号、只读密钥挂载、认证服务健康检查及网关路由配置。
- 独立 Compose 项目 erp-identity-container 的 identity 和 identity-mysql 均 healthy，Flyway/JPA 使用 identity_app 完成初始化，五种角色可查询。
- SHOW GRANTS 仅显示 cross_erp_identity 库权限，无全局管理权限。读取 mysql.user 返回 ERROR 1142，确认受限。

剩余：多角色敏感字段专项验收、旧认证数据清理迁移、完整容器链路切换与虚拟机发布。当前新增容器只验证 Identity 与其独立数据库；全链路公钥切换仍以上述 JVM 演练为证据。

## 完整容器链路补验

2026-11-16，erp-acceptance 通过 compose.production.yml + compose.identity.yml 启动七个容器，全部 healthy。将虚构旧账号通过 mysqldump 流导入独立认证数据库的暂存库，再执行 identity-migrate.sql。
实际从 Nginx 18080 端口请求：登录 200、JWT alg=RS256、me 200、orders 200、inventory/lots 200、refresh 200、logout 204。完整容器链路切换验证通过，虚拟机尚未更新。

## VMware 切换验收（2026-11-16）

以下依据用户提供的终端输出、截图及明确验收反馈：

- 停止前端、网关及业务后端进入维护窗口，配置和数据库完成备份，数据库备份退出码 0、文件非空；旧镜像已保留回退标签。
- 认证独立数据库启动，账号暂存导入及正式迁移退出码均为 0。
- 私钥读取权限错误经运行身份核查定位，修正为 UID 100/GID 101 后 Identity 健康。
- 叠加 compose.identity.yml 切换后七个容器全部 healthy。
- 用户使用原账号登录，截图显示 OWNER 及订单工作台；用户确认刷新恢复、业务菜单、退出后重新登录三项均通过。
- 虚拟机未执行破坏性回退，回退证据来自本地维护窗口演练。页面截图为空订单列表，不宣称虚拟机完成带业务数据全流程验收。

MS-07 认证服务迁移基线已完成。保留待办：旧密码/会话/暂存库清理、多角色敏感字段专项测试、SELinux Enforcing 适配和重启恢复验收。备份和密钥不得随 Git 发布。
