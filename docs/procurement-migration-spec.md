# MS-09 采购服务迁移规格（待确认）

## 目标与基线

MS-08 已在虚拟机完成正式迁移验收。MS-09 当前 procurement-service 仅有 Maven 模块，无源码和依赖。此项整体本地验收通过后统一部署虚拟机，再勾选正式任务完成。不删除旧 backend 或原采购表。

## 数据所有权

采购服务拥有 suppliers、purchase_orders、purchase_order_items、purchase_order_allocations、purchase_order_events 及采购创建幂等/认领工作流记录；自有数据库和受限账号。订单与采购需求权威仍在 Order，采购服务只维护安全本地投影，不读取 Order 数据库。员工展示从 Identity 同步，不复制密码、会话或客户联系方式。

旧Backend继续拥有包裹、收货记录、库存批次、分配、流水、打包。当前 DomesticPackage、GoodsReceiptItem、InventoryLot、InventoryAllocation 直接关联采购实体，需迁为本地 purchase_order_projections、purchase_item_projections、purchase_allocation_projections。投影保留原ID和商品/数量/分配来源字段，用Flyway回填与外键替换，不建立跨库外键。

采购数量/供应商/下单状态由采购服务权威维护。验货数量与收货事实由旧Backend权威维护：包裹、收货累计、超收检查、自动入库仍单库事务。采购服务通过收货事实事件更新到货显示及派生到货状态，不允许旧Backend再修改采购服务的采购实体。采购下单后不新增数量编辑或取消流程，以免引入未批准的跨服务竞态。

## API与规则

保持现有 /api/suppliers/**、/api/purchase-orders/** 请求响应、分页及错误契约；保留确认下单接口。每单一个供应商，每需求一次性完整转单，采购数量等于分配合计。OWNER和PROCUREMENT采购写权限；其他权限沿用原接口，不扩大授权。

前端继续只请求网关。采购创建保留 Idempotency-Key、请求快照和远程认领/确认/释放/恢复协议，复用既有Order内部接口及执行占用规则，不在远程调用期间持有本地事务。超时不可伪装成功，也不可盲目释放仍可能已提交的认领。

创建和确认下单事务同写Outbox。Broker确认发布、Inbox去重、版本防倒退、失败重试和死信可追踪。旧Backend仅在采购投影可用且已下单时允许登记收货；投影滞后返回可重试错误，不能读旧采购实体兜底。确认下单成功意味着采购权威已提交，不意味着异步收货投影已到达。

收货事件以receiptId标识，携带单调来源版本/累计验货数量；采购端按商品版本覆盖累计值，禁止重复加数。测试重复、乱序、Broker中断与重启恢复。

## 实施批次（均属于同一MS-09，不单独触发部署）

1. 独立应用、数据层、RS256验签、用户/订单/需求投影，复用既有API契约测试。
2. 供应商和采购查询、幂等创建/认领恢复、确认下单；真实MySQL并发测试。
3. 采购Outbox/旧Backend Inbox与安全投影、原关联替换；收货事实回传及并发超收验证。
4. 停写迁移与ID/数量对账、失败回滚、旧关联保留、网关受控切换、容器及浏览器完整履约验收。
5. 统一发布包、虚拟机实际数据核对和页面验收；完成后才勾选MS-09。

## 预计文件

services/procurement-service/pom.xml、src/main/java/**、resources/db/migration/**、src/test/**；backend/integration采购投影、receiving/inventory相关服务和实体、Flyway迁移及测试；gateway路由与测试；必要的frontend/api与错误状态适配；compose采购覆盖配置、docs/api-reference.md、迁移验收文档及HTML看板。不得顺带开始MS-10仓库拆分或MS-11打包拆分。

## 依赖确认

procurement-service当前没有依赖，拟添加已有项目使用的Spring Boot Web、Data JPA、Validation、OAuth2 Resource Server、AMQP、Flyway Core/MySQL、MySQL驱动和测试依赖，版本由现有父工程管理，不增加新的技术栈。按AGENTS第2条，需用户明确批准后修改pom。

## 验收与风险

必须验证：旧ID关联不丢、采购权威单写、同需求并发只转一次、同幂等键并发恢复、确认下单仅一次事件、收货和入库仍同事务、超收失败无半成品、采购与收货消息重复乱序不中断业务、两侧断网恢复、迁移中途回滚、浏览器全流程。切换后已有新写入时不能只退镜像或覆盖旧备份。

待确认决策：接受收货事实异步回传（短暂到货状态延迟，但超收由旧Backend本地累计强校验），以及本规格所列依赖与数据关联改造。确认前只写本规格，不实施数据库、认证或业务代码。
