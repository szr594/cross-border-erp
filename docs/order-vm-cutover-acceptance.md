# 订单服务虚拟机切换与回退验收

日期：2026-11-18。证据为操作者在 FinalShell 提供的命令输出和页面验收反馈。

## 当前运行结论

虚拟机已回退至切换前镜像与配置，前端恢复后用户确认页面无问题。新订单路由未作为最终运行状态保留。不得将本次演练记为全量业务迁移上线完成。

## 已验证

- 两侧订单、商品、采购需求、事件、渠道、店铺和 CSV 批次/行/错误表均为空，因此没有执行历史业务数据复制。
- 停写后备份业务库与订单库，并记录原镜像 ID 和配置。备份目录为 /opt/cross-border-erp/backups/cutover-20261118-141053；备份内容不进入 Git。
- 候选 Backend、Order、Gateway 和修复版 Identity 均健康。
- Identity 用户目录、Backend 履约事实内部调用通过。
- 使用原账号通过新 Gateway 登录，/api/auth/me、/api/orders、/api/orders/filter-options 均返回 200。
- 前端关闭期间回退；回退前订单、商品、需求、导入批次数量合计在两个库均为 0。
- 使用备份记录的镜像 ID 重新打 rollback-20261118 标签，恢复原配置；服务健康并恢复前端，用户确认页面通过。

## 现场修复

DirectorySecurity 配置类默认 Bean 名与方法名冲突导致 Identity 启动失败。方法改为 directorySecurityFilterChain，并新增 MVC/Security 上下文注册回归测试。现场使用 cutover-20261118-fix1 镜像，不启用 Bean 覆盖。

RabbitMQ 已完全启动且无本地告警，但原 5 秒健康检查连续超时。现场运行队列最高 89；保留 ping 检查，设置 interval 30s、timeout 30s、retries 10、start_period 90s 后健康。保留原持久卷，不删除消息数据。

## 回退后管理命令

```bash
cd /opt/cross-border-erp
docker compose --env-file .env.production \
  -f compose.production.yml \
  -f compose.identity.yml \
  -f compose.order.yml \
  -f order-upgrade/compose.order-upgrade.yml \
  -f order-cutover/compose.runtime.yml \
  -f order-cutover/compose.rollback.yml ps -a
```

## 验收边界

此次完成的是无业务写入情况下的镜像、配置和路由切换回退。没有覆盖虚拟机真实数据库备份恢复、历史业务关联移交或切换后新增业务数据反向同步。数据库导出恢复仅在本地隔离 MySQL 测试库验证。虚拟机查询返回空列表，只证明认证与查询链路可用，不能代替带数据履约验收。再次正式切换前仍需复核数据、恢复点和部署配置，并保持单侧写入。
