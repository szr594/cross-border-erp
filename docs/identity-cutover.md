# Identity 切换与回退操作约定

## 容器配置

在 compose.production.yml 基础上叠加 compose.identity.yml：

```bash
docker compose --env-file .env.production -f compose.production.yml -f compose.identity.yml config --quiet
```

新增环境变量见 .env.production.example。IDENTITY_KEYS_DIR 必须指向包含 private.pem（PKCS8）和 public.pem（X509）的受控目录，认证容器内非 root 用户需有读取权限。网关与旧后端仅挂载公钥文件。
为减少跨库误授权，Identity 使用独立 MySQL 容器；identity_app 仅拥有 cross_erp_identity 库权限，迁移管理凭据不注入应用。
注意：deploy/identity-migrate.sql 适用于源与目标逻辑库位于同一验收实例。独立实例生产迁移需先用受控离线账号将旧认证表导出到目标实例的隔离暂存库 cross_border_erp，再执行脚本。暂存数据包含密码哈希，不可提交 Git；完成核验和回退窗口后另行审批清理。不得直接用脚本跨容器读取另一实例。
不要直接在虚拟机执行 up 切换；先完成数据导入及密钥配置，再在维护窗口统一发布。

状态：2026-11-16 已完成隔离 MySQL/JVM 迁移回退、完整容器链路，以及 VMware 切换页面验收。见 identity-cutover-acceptance.md。备份、旧镜像及暂存库继续保留，遗留数据清理单独批准。

切换模式要求两个配置同时发布：网关设置 IDENTITY_SERVICE_URL，网关与旧 backend 均设置 IDENTITY_PUBLIC_KEY=file:/run/keys/public.pem。

Linux 权限：当前镜像运行身份为 UID 100、GID 101。密钥目录及 private.pem 所有者应为 100:101，目录权限 700、私钥 400、公钥 444；不得假设运行 UID 为 1000。挂载前用 `docker run --rm --entrypoint id cross-border-erp-production-identity:latest` 核对身份。VMware 的 SELinux 当前为 Permissive，Enforcing 部署需另验挂载标签。
旧 backend 设置 APP_AUTH_LEGACY_ENABLED=false，关闭旧认证控制器、签发服务和初始化器。未配置公钥时仍使用旧 HS256，仅用于现有基线和回退；提供无效公钥应启动失败，不能自动降级。

Identity 单独读取私钥、公钥和 key-id，数据库账号只允许访问自己的逻辑库。私钥、公钥不随源码或镜像发布，使用文件只读挂载。生成 RSA 至少 2048 位，私钥 PKCS8、公钥 X509。

## 数据迁移

维护窗口停止认证和业务写入，备份旧数据库及环境配置。目标库先通过 Identity 的 Flyway 初始化。
受控离线迁移账号执行 deploy/identity-migrate.sql，业务服务账号不得获授跨库权限。
脚本保留用户 ID、哈希、角色关联和锁定状态，不迁移刷新会话；非空目标用户库或角色映射不一致时拒绝执行。
迁移失败需要先排查错误，并清理脚本遗留的 import_legacy_identity 存储过程再重试；不要删除目标业务数据来绕过非空检查。
旧库数据在回退窗口内隔离保留，暂不执行密码哈希和会话清理；此清理单独安排 Flyway 变更。

## 回退

关闭入口并停止 Identity 写入，恢复旧版 Compose、旧 JWT_SECRET 和旧认证路由，移除 IDENTITY_PUBLIC_KEY、IDENTITY_SERVICE_URL，恢复 APP_AUTH_LEGACY_ENABLED=true。
不恢复整库覆盖切换后的业务数据；当前回退只允许在维护窗口、尚未开放业务写入时执行。
恢复旧服务后统一重新登录，验证订单负责人和库存操作人显示，随后才开放入口。

## 待演练

公钥模式需验证网关与旧后端拒绝 HS256、接受新 RS256；旧认证路径关闭后 /api/auth/me 的 401 仍可作为现有探针，但认证路由必须落在 Identity。
用虚构原账号执行迁移，确认 ID 与密码不变、旧业务关联可用、重复迁移失败，再完成切换与回退后的登录和业务查询验证。
