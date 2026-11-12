# Cross Border ERP

一个面向作品集展示、使用 Java 与 Vue 重构的跨境电商订单履约 ERP。项目使用虚构演示数据，覆盖从销售订单、采购、国内收货、库存分配到打包发货的完整业务流程。

## 技术栈

- Java 17、Spring Boot 3
- Maven
- Spring Data JPA、MySQL 8.4
- Flyway
- Vue 3、TypeScript、Vite、Vue Router
- Element Plus
- pnpm

## 当前阶段

项目已经实现 JWT 认证，以及订单 CSV 导入、订单管理、采购、收货、库存和打包组成的完整 MVP 业务闭环。库存模块支持可追溯批次、自动与人工分配、释放分配、库存调整、隔离库存转可用，以及订单取消时自动释放库存预留。打包模块支持查询待打包订单、多箱商品分配、不可变的打包完成记录，以及在同一事务中原子扣减已预留库存。仅老板角色可使用 CSV 导入工作台；系统会先校验项目规定的 CSV 模板，再以原子操作创建订单，并在不保留原始文件的前提下提供安全的行级错误信息。

使用 `dev` 配置并设置 `DEMO_OWNER_PASSWORD` 后，项目启动时会创建虚构订单、采购单、两批收货记录、合格品与破损品库存批次、已预留与已释放的库存分配、一张可直接进入打包流程的完整预留订单，以及不可变库存流水。重复启动不会重复生成这些演示记录。

## 本地开发

根据 `.env.example` 创建本地 `.env`。使用 `MYSQL_ROOT_PASSWORD` 设置 MySQL 容器的 root 密码，使用 `MYSQL_PASSWORD` 设置后端数据库连接密码。不要提交本地 `.env`。`JWT_SECRET` 必须使用至少 32 字节的随机值。只有需要在开发环境创建 `owner` 演示账号时，才设置 `DEMO_OWNER_PASSWORD`。

启动数据库：

```bash
docker compose up -d mysql
```

MySQL 默认绑定到 `127.0.0.1:3306`，字符集为 `utf8mb4`，应用时间统一以 UTC 保存。

启动后端：

```bash
cd backend
mvn spring-boot:run
```

启动前端：

```bash
cd frontend
pnpm install
pnpm dev
```

默认开发地址：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`

认证采用有效期 15 分钟、仅保存在前端内存中的 JWT Access Token，以及有效期 7 天、支持轮换的 HttpOnly Refresh Token Cookie。详细规则见 `docs/auth-spec.md`。

## 验证

```bash
cd backend
mvn test

cd ../frontend
pnpm type-check
pnpm build
```

订单导入、库存和打包流程由后端集成测试、前端类型检查和生产构建共同覆盖。每个端到端模块里程碑完成后，还会执行一次浏览器人工验收。

开发新的业务模块前，请先阅读 `docs/mvp-spec.md`、`docs/architecture.md` 和 `docs/route-design.md`。
