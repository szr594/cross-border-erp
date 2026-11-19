# MS-09 部署与恢复验收（2026-11-19）

证据来源：本地实际测试与容器/Playwright输出，以及用户通过FinalShell执行的完整终端输出和页面检查反馈。没有通过SSH直接操作虚拟机。

## 最终状态

VMware当前已恢复MS-09采购路径：Gateway PROCUREMENT_SERVICE_URL=http://procurement:8083；六个项目服务使用ms09-20261119-candidate1镜像；12个服务全部healthy，HTTP入口返回200，用户确认订单、采购、库存、收货、打包页面正常。未停留在回退状态。

## 已验证

- 本地：真实MySQL/RabbitMQ/三个业务JVM完整CSV→需求→采购→收货→入库→打包；采购与收货双向消息，重复事件去重，Broker停止/启动及应用重启恢复。
- 本地：采购消费者提交后不ACK并断开真实通道，Broker重投后效果唯一；新增采购单、商品、分配、供应商与事件的反向回填、冲突事务回滚及LEGACY权威交接。
- 虚拟机：原采购/收货0、无PENDING认领和待发送事件；旧业务Flyway16→19，独立采购Flyway4；维护迁移六表各0，进程成功退出。
- 虚拟机：原订单3单保留，生产采购路由切到独立服务，页面正常。
- 虚拟机：整机重启后boot_id、镜像与重启策略、容器健康、HTTP入口检查通过；Order/Procurement重启前后计数无diff。
- 虚拟机：空数据回退后Gateway采购目标为空，采购投影消费者关闭，独立采购服务exited，其余服务healthy、订单3单保留、用户确认页面正常。
- 虚拟机：再次确认两侧采购六表合计均0后恢复MS-09；Gateway目标重新为http://procurement:8083，12服务healthy，HTTP200、用户确认页面正常。

## 备份与运行配置

切换前备份：/opt/cross-border-erp/backups/ms09-20261119-161943。
回退前备份：/opt/cross-border-erp/backups/ms09-reverse-20261119-174306。
以上仅记录路径，备份内容和凭据不进入仓库。

最终运维使用 /opt/cross-border-erp/ms09-compose.sh --cutover；沿用既有Compose覆盖并叠加MS-09采购/runtime/cutover覆盖。必须保留compose.rabbitmq-runtime-fix.yml，否则会丢失服务端和CLI线程限制。不要只用旧Compose启动全部服务，以免撤销采购路由。

## 限制与待改进

- 2核、7.6GiB虚拟机冷启动出现长时间CPU满载，约18分钟时Backend/RabbitMQ仍不健康，后续自然恢复。此次只通过最终恢复，不代表恢复时间达标。
- 虚拟机采购为空，没有验证切换后新增采购数据的虚拟机回退；不能把本地非空MySQL专项写成虚拟机成果。反向维护进程完整日志未粘贴，虚拟机回退证据以路由、服务状态、计数和页面为限。
- 没有在虚拟机整机重启前注入待处理采购消息，不能据空计数一致声称整机消息补偿验收通过。
- 本地浏览器部分写入使用接口驱动；完整打包业务闭环由三服务接口专项覆盖。旧演示店铺初始化入口在独立Order模式不可用，验收店铺使用隔离fixture。
- 此次未提交Git，未推送；仓库文档更新后未重新上传到虚拟机发布包。
