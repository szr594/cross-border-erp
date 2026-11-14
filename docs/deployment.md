# VMware Linux 部署

## 首次管理员初始化

数据库健康且后端镜像构建完成后，在 Linux 终端执行（隐藏读取密码，不写入 shell 历史）：

```bash
read -r -p '管理员登录名: ' INITIAL_OWNER_LOGIN
read -r -s -p '管理员密码（至少12位）: ' INITIAL_OWNER_PASSWORD
echo
export INITIAL_OWNER_LOGIN INITIAL_OWNER_PASSWORD
docker compose --env-file .env.production -f compose.production.yml run --rm --no-deps -e INITIAL_OWNER_LOGIN -e INITIAL_OWNER_PASSWORD backend --spring.profiles.active=prod,init-owner --server.port=0
unset INITIAL_OWNER_LOGIN INITIAL_OWNER_PASSWORD
```

初始化仅允许用户表为空；已有账号时拒绝执行。成功后进程退出。正常 `prod` 启动不会创建账号。请仅运行一个初始化进程。

## 可信代理

Nginx 位于专用 `172.29.84.0/24` 网络，固定地址 `172.29.84.10`。网关只接受该 TCP 来源的 `X-Real-IP`，Nginx 使用实际连接地址覆盖此头；网关随后清除该头。外部请求伪造该头不会改变限流身份。若该网段与虚拟机已有网络冲突，需同步修改 Compose 子网、Nginx 固定 IP 和 `TRUSTED_PROXY_IP`。

在安装了 Docker Engine 和 Compose 插件的 Linux 虚拟机上，从项目根目录执行以下命令。构建需要联网下载镜像和 Maven/pnpm 依赖。

```bash
cp .env.production.example .env.production
chmod 600 .env.production
# 编辑 .env.production，填写独立数据库密码及至少 32 字节的随机 JWT_SECRET
docker compose --env-file .env.production -f compose.production.yml config --quiet
docker compose --env-file .env.production -f compose.production.yml up -d --build
docker compose --env-file .env.production -f compose.production.yml ps
```

访问 `http://虚拟机IP`。仅 Nginx 的 HTTP_PORT 对外发布，其余服务通过 Compose 网络通信。虚拟机防火墙放行网站端口，SSH 按需开放。

生产配置使用 `prod`，不会自动创建演示账号。首次登录前执行本文的一次性初始化命令。

Redis 使用 AOF，MySQL 和 Redis 使用命名卷。`down` 保留卷；不要执行 `down -v`，它会删除数据。升级前备份数据库，再执行 `up -d --build`。Flyway 在后端启动时执行迁移。

```bash
docker compose --env-file .env.production -f compose.production.yml logs --tail=100 gateway backend
docker compose --env-file .env.production -f compose.production.yml down
```

当前提供局域网 HTTP 配置，`SECURE_COOKIE=false`。公网使用前需配置域名、HTTPS 并设置 `SECURE_COOKIE=true`。

限流使用经过可信代理检查的客户端 IP，登录每分钟 10 次、刷新每分钟 30 次。

后端健康检查预期匿名访问 `/api/auth/me` 返回 401；网关检查 `/actuator/health`，Nginx 检查首页。镜像构建跳过测试，发布前需先在源码环境运行 `mvn test` 和前端检查。
