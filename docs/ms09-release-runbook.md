# MS-09 发布与回退步骤（准备中）

发布包中覆盖文件均位于根目录；仓库路径deploy/compose.ms09-*.yml上传后使用同名根文件。候选组合须再叠加compose.ms09-runtime.yml；切换时最后叠加compose.ms09-cutover.yml。

当前候选配置：compose.production.yml + compose.identity.yml + compose.order.yml + compose.procurement.yml。虚拟机还须保留已验收的MS-08镜像及runtime覆盖文件；不能用仓库latest标签直接替换运行镜像。

## 统一包必须包含

- backend、order、procurement、gateway、identity、frontend固定版本镜像及镜像ID清单。
- 生产/认证/订单/采购Compose、已验收runtime覆盖、显式采购路由切换覆盖、SHA256SUMS。
- 维护迁移说明、备份/回退/验收脚本与验收结果。

固定版本候选离线包已构建：output/ms09-ms09-20261119-candidate1。包含六个项目镜像及MySQL/Redis/RabbitMQ；镜像清单注明当前工作区尚未提交。12服务本地容器全部healthy，浏览器登录、CSV导入、采购确认及业务页面加载和收货入库验收通过。虚拟机迁移与重启仍未执行。

## 环境参数

PROCUREMENT_DATABASE_PASSWORD、PROCUREMENT_MYSQL_ROOT_PASSWORD通过环境文件提供；MS09_IMAGE_TAG指定固定标签；沿用IDENTITY_KEYS_DIR、ORDER_INTERNAL_SERVICE_TOKEN及RabbitMQ账号。采购数据库仅容器网络开放，应用使用procurement_app受限账号。不得把环境文件或数据库备份加入发布包/Git。

## 正向迁移

1. 停止外部写入并备份旧库、采购库、Compose及镜像；处理未完成认领。
2. 候选采购服务准备员工和需求投影，核对来源ID；停业务写入及消息消费者/发布器后运行维护任务。
3. APP_PROCUREMENT_MIGRATION_ENABLED=true；APP_PROCUREMENT_MIGRATION_DIRECTION=forward；APP_EVENTS_ENABLED=false；APP_PROCUREMENT_REMOTE_ENABLED=false。PROCUREMENT_DATABASE_*指目标采购库，PROCUREMENT_MIGRATION_SOURCE_*指旧业务库（仅维护时提供）。
4. 维护任务保留原ID并对账，发布采购快照。核对旧后端投影完成权威交接、现有收货记录不丢，再应用deploy/compose.ms09-cutover.yml。
5. 验证登录、采购、收货、库存及打包。全部通过后保存boot_id和事件基线，经FinalShell重启整机并验收。

## 切换后新增数据回退

1. 所有业务停写、处理PENDING认领，排空采购及收货消息并核对累计；保留两侧备份。
2. 维护方向reverse，PROCUREMENT_DATABASE_*指权威采购库，PROCUREMENT_MIGRATION_SOURCE_*此时指回填目标旧业务库。
3. 反向工具保留新增采购、商品、分配、事件、幂等键；校验身份与版本，拒绝遗留独有记录及未对齐收货事实。目标回填同库事务，全字段复制校验，失败整体回滚。
4. 数据回填成功尚不等于回退完成：必须验证旧后端本地投影与采购权威标记交接，再切旧路由。反向工具已在同事务将已对齐采购投影切回LEGACY；回退覆盖compose.ms09-rollback.yml单独禁用采购快照消费者并恢复旧采购路由。必须先停采购服务、停止两侧相关消费者并排空队列，防止旧消息重新夺取权威。虚拟机反向回退尚未演练；禁止只回退镜像、只恢复切换前旧备份。

## 已验收范围

真实MySQL反向回填含新增采购单及分配、冲突回滚；真实Order/Procurement/Backend三服务CSV→需求→采购→收货→库存→打包接口闭环。浏览器、容器部署与整机重启尚未完成，不能勾选MS-09整体完成。


## 最终补充：MS-09恢复完成（2026-11-19）

用户完整终端输出确认两侧采购六表合计0后逐服务恢复MS-09，最终网关目标http://procurement:8083、12服务healthy、HTTP200；用户确认页面正常。空数据回退路径与恢复演练完成，当前不是回退模式。看板MS-09按当前数据规模部署验收完成，冷启动慢与虚拟机非空回退/带消息整机重启未覆盖的限制仍保留。完整证据与边界见ms09-vm-acceptance.md；前文“尚未部署/未重启”等为较早阶段记录。
