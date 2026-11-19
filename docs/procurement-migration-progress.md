# MS-09 采购服务迁移进度

## 第一批：独立数据层与认证基础（2026-11-18）

已新增 procurement-service Spring Boot 入口、Web/JPA/Validation/RS256/AMQP/Flyway/MySQL及测试依赖（用户已批准）。默认端口8083，数据库只接受PROCUREMENT_DATABASE_URL/USER/PASSWORD配置。没有默认业务账号或密码，也没有公开采购业务接口。

V1建立员工显示与订单/商品/需求安全投影；V2建立供应商、采购单、采购明细、分配、事件表及bridge_confirmed状态。采购实体不依赖旧Backend Java包，不建立跨库外键。MySQL采购关联使用显式FOREIGN KEY，防止内联REFERENCES被忽略。

已新增采购实体及五个Repository、员工投影Repository、需求投影实体。订单和商品投影的完整消费者/实体查询在后续批次补齐。

实际验证：mvn -pl services/procurement-service -am test -q；mvn -pl services/procurement-service test -q（H2）；设置PROCUREMENT_TEST_DATABASE_URL/USER/PASSWORD为新隔离MySQL8.4库后执行同一test命令。三项测试均通过：Flyway与JPA启动、采购与投影表归属且无app_users/sales_orders、匿名和无效JWT拒绝、正确RS256签名issuer接受且错误issuer拒绝。身份密钥随机生成，仅测试使用。git diff --check通过。

本批没有供应商/采购业务API迁移、认领工作流、投影消费、旧收货库存关联替换、网关路由或虚拟机部署。不能将本批编译启动通过视为整个MS-09完成。下一批迁移供应商API及采购查询契约，然后迁移创建/认领恢复/确认下单命令。

## 第二批：供应商API与采购查询（2026-11-18）

已迁入SupplierController/SupplierService，支持列表、详情、创建、修改及启停，保留OWNER/PROCUREMENT写权限与既有重复/版本冲突契约。PurchaseOrderController/PurchaseOrderQueryService支持列表、详情、事件分页，按原规则隐藏bridge_confirmed=false采购单。

采购来源通过本地ProcurementRequestProjection→OrderItemProjection→OrderProjection关联返回原salesOrderId、salesOrderNo、salesOrderItemId和procurementRequestId，不访问旧数据库或旧Backend Java模型。补齐查询所需DTO及Repository/Specification，尚未接入投影消息消费者。

实际命令：mvn -pl services/procurement-service -DskipTests compile -q；mvn -pl services/procurement-service test -q；以PROCUREMENT_TEST_DATABASE_URL/USER/PASSWORD指向新隔离MySQL8.4库后再次运行同一test命令；git diff --check。全部通过，基础3项＋查询契约2项共5项，H2与MySQL各验证一轮。

测试覆盖：供应商非授权写403、正确创建201、重复409、列表及详情、停用和旧版本启用409；采购列表数量、来源安全投影关系、事件分页、未确认采购单404、非法排序及页码400。来源数据仅测试SQL造数，不能标记消费者验收完成。

未新增网关采购路由、未修改旧后端运行路径、未部署虚拟机、未提交Git。下一批继续采购创建/认领确认恢复及确认下单命令，之后完成消息投影、收货事实回传与旧关联替换；整项MS-09完成后再部署。

## 第三批：采购命令与认领恢复基础（2026-11-18）

独立服务迁入PurchaseOrderCommandService（仅远程认领后创建，不保留旧订单表分支）、RemoteProcurementClaims、持久化PurchaseBridgeWorkflow及定时恢复、PurchaseOrderingService与创建/确认下单控制器。采购创建需Idempotency-Key，仅在app.procurement.remote-enabled=true且配置Order内部地址/令牌后注册创建入口。普通查询基础模式不假装支持本地旧表转单。

V3建立purchase_create_operations状态、请求/响应快照、租约、重试时间、错误和采购单关联约束；不修改已发布V1/V2。采购需求来自本地投影，校验版本和完整数量，缺失返回可重试错误。远程认领、确认、释放不在本地数据库事务内调用。未完成认领确认的本地采购单继续隐藏。

实际验证：mvn -pl services/procurement-service -DskipTests compile -q；新隔离MySQL8.4库执行 mvn -pl services/procurement-service '-Dtest=ProcurementCommandRecoveryIT' test -q，通过1项集成测试；mvn -pl services/procurement-service test -q，常规5项全部通过。git diff --check通过。

MySQL测试模拟远端确认已成功但响应丢失，校验首次返回503、列表隐藏、本地只有1单，恢复及同键重放返回同一采购单，下单重放响应一致且ORDERED事件仅1条。远端为受控Mockito替身，不等于真实Order服务RPC或消息链路已验收。消费者/用户目录同步尚未接入，测试手动准备安全投影。

仍需：认领释放失败和进程重启/租约竞争专项，真实Order+采购服务HTTP链路及并发、Outbox/Inbox和收货回传、旧关联替换。本批不部署虚拟机，不标记MS-09整体完成，代码未提交。

## 真实跨服务、租约与重启恢复专项（2026-11-18）

新增 ProcurementRealBridgeIT：启动真实Order与独立采购服务两个JVM，使用独立MySQL8.4库、随机RSA验签和真实HTTP。故障代理仅用于转发及丢弃确认响应，不用Mockito替代Order业务。订单/需求与采购本地投影由隔离测试造数，本轮未验证消息消费者。

实际命令：mvn -pl services/procurement-service -DskipTests package -q；mvn -pl services/procurement-service '-Dtest=ProcurementRealBridgeIT' test -q；git diff --check。三项测试通过，失败0、错误0、跳过0。

1. 真实采购POST经HTTP认领、确认到Order，需求成为CONVERTED；同键重放返回相同响应，持久化操作SUCCEEDED。
2. 两线程同键竞争租约，响应只允许201或可重试503；最终操作成功、同需求仅一条本地采购分配，重放201。
3. Order实际提交CONFIRMED后代理扣留响应，强杀采购JVM；数据库仍PENDING且采购单不可见。重启真实采购进程，等待真实30秒租约自然到期，定时恢复至SUCCEEDED；重放原请求201、原分配仍仅一条且采购单可见。没有手工推进租约、操作状态或订单认领状态来使测试通过。

范围：验证进程重启而非整台虚拟机重启；不等于采购Outbox/旧Backend投影消费者、收货事实回传或跨服务释放失败场景已完成。后续继续采购事件与本地投影、收货事实回传及旧关联替换。整个MS-09仍进行中，不部署虚拟机，代码未提交。

## 事件基础批次（2026-11-18，链路尚未接通）

V4新增采购事件版本、procurement_outbox、procurement_event_inbox及采购端收货数量投影。ProcurementOutbox仅发布白名单字段，生成独立聚合revision，认领确认可见与确认下单事务同写采购快照。未认领确认的采购单禁止发布。ProcurementOutboxPublisher保留持久消息及Broker确认后标记发布的机制，按app.events.enabled显式启用。

ReceivingFactsConsumer按purchaseOrderItemId与sourceVersion维护验货/合格累计值；同eventId去重、旧版本不覆盖、新版本禁止倒退和超采购量，同版本不同数量拒绝。收到有效新累计值时派生PARTIALLY_RECEIVED/RECEIVED状态，不进行重复加法。

mvn -pl services/procurement-service test -q 在H2及新隔离MySQL8.4库各执行一轮，6项均通过；新增累计1→重复1→累计2→旧累计1场景最终仍为2且RECEIVED。git diff --check通过。

尚未实现：旧Backend采购投影表/消费者、收货事实Outbox发送与Rabbit绑定、DomesticPackage/GoodsReceiptItem/InventoryLot/InventoryAllocation旧采购JPA关联及Flyway外键替换。当前ReceivingFactsConsumer仅由测试直接调用，不能声称消息回传链路或旧关联替换完成。MS-09继续进行中，不部署虚拟机，代码未提交。


## 双向真实消息验证（2026-11-19）

旧后端新增采购投影消费者、Inbox、采购/明细/分配本地投影；V18回填历史采购，V19替换包裹、收货明细、库存批次和库存分配四类外键。收货同事务记录receiving_outbox，采购服务监听收货累计事实。旧模式兼容同步仅允许LEGACY权威数据。

收货、演示数据及打包专项13项通过。MySqlReceivingConcurrencyIT、MySqlReceivingSchemaIT、MySqlInventoryPostingIT在隔离MySQL8.4库执行通过。

新增ProcurementMessagingIT：两个真实应用JVM、两个独立MySQL库、隔离RabbitMQ vhost；采购Outbox发布到旧后端投影，旧后端收货Outbox发布到采购消费者；合格1、验货2得到received_quantity=1及RECEIVED。重置两端published_at重发相同消息后，两端Inbox各1条，数量仍为1。测试1项通过，无跳过。真实验证发现并修复两端publisher-confirm-type缺失，配置为simple。

验证命令：mvn -pl backend,services/procurement-service -am package -DskipTests -q；mvn -pl backend -am test -q -Dtest=ProcurementMessagingIT -Dsurefire.failIfNoSpecifiedTests=false。均退出0；git diff --check通过。

范围限制：本专项从真实Outbox记录驱动消息链路，未覆盖采购创建/页面收货到Outbox的单次端到端流程；未模拟Broker宕机或真实ACK丢失、虚拟机重启。重复发布验证不等同于ACK丢失故障演练。MS-09整体验收和虚拟机部署仍未完成。


## Broker中断与应用重启恢复（2026-11-19）

ProcurementMessagingIT扩展真实故障注入：仅停止专用erp-events-it-rabbit测试容器。采购方向在Broker停止时写入采购Outbox，确认记录仍未发布且旧后端没有投影；强制结束并重启采购JVM，再启动Broker，投影自动落库。收货方向同样停止Broker、写入收货Outbox，确认采购合格数仍0且Outbox待发布；强制结束并重启旧后端JVM，恢复Broker后采购状态自动变为RECEIVED、合格数为1（验货2）。恢复后两方向再次发布相同事件，Inbox各保持1条，合格数仍1。

执行：mvn -pl backend -am test -q -Dtest=ProcurementMessagingIT -Dsurefire.failIfNoSpecifiedTests=false。退出0，1项测试通过、0失败、0错误、0跳过。测试finally恢复Broker并终止专属应用进程。git diff --check通过。

本次覆盖实际Broker容器停止/启动、生产者应用进程强制退出/重启、双向持久Outbox补发及重复事件去重；未覆盖整台虚拟机重启、提交后ACK丢失故障窗口、页面全流程。未修改虚拟机，未提交Git。MS-09整体仍进行中。


## 采购消费真实ACK丢失窗口（2026-11-19）

ProcurementAckLossIT使用隔离MySQL库、隔离RabbitMQ vhost及真实消费者事务。停止监听容器，以basicGet(autoAck=false)取消息；调用采购投影消费者并确认Inbox事务已提交；不发送basicAck，直接abort真实目标通道。重启监听容器后验证相同消息确实再次调用消费者（至少2次），Inbox仍1条、采购商品投影1条、分配数量仍2。专项1项通过、0失败、0错误、0跳过。

执行：mvn -pl backend -am test -q -Dtest=ProcurementAckLossIT -Dsurefire.failIfNoSpecifiedTests=false，退出0。

新增deploy/verify-ms09-vm-reboot.sh，before保存boot_id、应用容器及镜像/重启策略；after验证boot_id变化、Docker服务、IP转发、容器恢复及HTTP入口。脚本不主动重启、不修改数据库，未部署采购服务时拒绝本阶段验收。脚本尚未在Linux执行，不能认定整机重启通过。当前虚拟机仍为此前版本，MS-09需整项本地验收、构建部署包后通过FinalShell部署，再执行整机重启与事件ID/数量核对。

本次采购ACK窗口通过不等于收货回传消费者同一故障窗口已专项覆盖；收货已覆盖Broker中断、应用重启和重复发布。未提交Git，未更新虚拟机。


## 部署缺口补齐第一批（2026-11-19）

采购服务复用订单服务已有Identity员工展示目录协议；已验签请求在进入业务事务前同步安全展示投影，目录失败返回503。保持无Identity地址的隔离测试模式；生产Compose仍需强制配置Identity地址及内部令牌。

网关增加PROCUREMENT_SERVICE_URL，可配置切换suppliers/purchase-orders整组路由；未配置走旧后端，收货/库存/打包仍留旧后端。OrderRouteTest新增采购目标、仓库归属及非法地址拒绝断言。

mvn -pl services/procurement-service,services/gateway-service -am test -q退出0；新增路由断言后mvn -pl services/gateway-service -am test -q -Dtest=OrderRouteTest -Dsurefire.failIfNoSpecifiedTests=false退出0。

未完成：需求事件自动同步、独立目录同步故障/版本专项、历史采购迁移对账及回退、采购Compose/Dockerfile、完整履约浏览器验收及虚拟机部署。不能据本批切换生产。


## 需求自动投影第二批（2026-11-19）

新增DemandSnapshotConsumer及procurement.order-sync持久队列、order.#绑定和死信队列。消费Order的schema3订单快照及order.procurement.snapshot.v1需求快照，将白名单字段投影到采购端自有表；Inbox与业务更新同事务，锁订单/需求行，检查来源身份、数量及同版本状态冲突，旧版本不覆盖新状态。

新增DemandSnapshotContractTest：重复消息不重复写入，旧REQUESTED不能覆盖CONVERTED，同版本冲突拒绝；继承收货累计测试一并执行。采购服务全部单元/契约测试退出0；同测试在独立MySQL8.4库3项通过、0失败/错误/跳过；git diff --check通过。

真实Order生产者→新需求消费者的Broker链路仍需补充专项；现有双向采购/收货Broker专项在需求绑定加入前执行，不能替代本批验收。历史数据迁移对账和回退、统一部署包、浏览器全流程仍未完成，不部署虚拟机。


## 真实需求生产与采购认领链路（2026-11-19）

ProcurementRealBridgeIT改为真实订单HTTP接口创建采购需求，Order事务写Outbox，经真实RabbitMQ隔离vhost自动消费到采购需求投影；删除测试直接写入采购端订单/商品/需求投影的步骤。随后实际HTTP认领/确认、同幂等键并发、远程确认但响应丢失并强杀采购进程后的租约恢复全部通过。测试用户展示与供应商仍使用隔离fixture，不能视为Identity目录或供应商页面端到端验收。

构建Order与Procurement最新JAR成功；mvn -pl services/procurement-service -am test -q -Dtest=ProcurementRealBridgeIT -Dsurefire.failIfNoSpecifiedTests=false退出0，3项通过、0失败/错误/跳过。RabbitMQ管理接口测试客户端固定HTTP/1.1，避免HTTP升级造成EOF。

后续仍需历史数据迁移及回退工具、统一Compose/镜像部署包、浏览器完整履约验收。当前未部署虚拟机，未提交Git。


## 历史采购维护迁移实现（2026-11-19，专项待验证）

新增ProcurementDataMigrationService和显式维护启动入口APP_PROCUREMENT_MIGRATION_ENABLED。要求采购目标业务表、Outbox及累计投影为空；源库无PENDING认领及未确认采购；目标员工目录及需求投影已准备且数量一致。源库只读可重复读，目标写入单事务，按目标列白名单保留采购表及已完成幂等记录原ID；从收货事实累计合格/破损，检查合格数与旧采购累计一致、验货不超采购量，初始化采购收货投影并同事务写采购快照Outbox。禁止维护迁移期间启用事件发送。

mvn -pl services/procurement-service -am test -q退出0；git diff --check通过。该结果是现有回归，不代表新迁移流程专项通过。真实MySQL历史数据复制、失败中途回滚、源目标逐字段对账及切换后反向回退仍需补专项；不得直接用于虚拟机切换。源库凭据只能在维护环境临时提供，不进入仓库或常规运行配置。


## 历史迁移真实MySQL专项（2026-11-19）

新增ProcurementMigrationIT，每次创建唯一隔离MySQL源目标库。六张采购表复制后，在收货累计校验故意制造accepted与received不符；确认六表及采购Outbox全部回滚为空。修复源累计后重试，原采购ID71、供应商ID41、商品ID81及分配ID91关联保留，收货投影合格1/验货2，采购Outbox1条，源数量不变；非空目标重复迁移被拒绝。

mvn -pl services/procurement-service -am test -q -Dtest=ProcurementMigrationIT -Dsurefire.failIfNoSpecifiedTests=false退出0，1项通过、0失败/错误/跳过；更换唯一目标库后复跑同样通过；git diff --check通过。

此专项验证维护迁移事务原子回滚与修复重试，不等于生产切换后的反向数据回退。尚需覆盖完整历史幂等记录、全字段对账及切换后新增写入回退，统一部署包、容器与浏览器全流程；未更新虚拟机、未提交Git。


## 反向回填、部署配置与完整接口闭环（2026-11-19）

新增ProcurementRollbackService和维护direction=reverse。停写并排空采购Outbox后，事务回填旧库，保留新增供应商/采购/商品/分配/事件/幂等记录；逐字段校验、身份冲突及旧库版本更高拒绝，先核对旧库收货事实，禁止远端旧累计覆盖仓库新数据。MySQL专项覆盖切换后新增采购72、商品82、分配92、供应商42及事件99；故意制造分配冲突，回填全部回滚，修复后恢复、重复恢复一致。1项通过、0失败/错误/跳过。

新增采购Dockerfile、compose.procurement.yml（独立受限数据库账号、不映射数据库端口、重启策略、健康检查）和deploy/compose.ms09-cutover.yml。占位环境执行五文件docker compose config --quiet退出0。仅配置验证，不代表镜像/容器部署通过，统一离线镜像包尚未生成。

新增ProcurementFulfillmentIT，三个独立应用JVM、独立MySQL库、真实RabbitMQ：CSV导入→需求→采购创建/确认→采购投影→收货→库存自动预留→装箱完成→订单PACKED；确认已消耗库存阻止取消、旧后端原订单/采购表为空，采购收货状态收到回传。发现ReceivingCommandFacade执行授权SQL遗漏，已改为采购投影关联。最终专项1项通过、0失败/错误/跳过。

剩余：反向回填后旧后端投影权威切回工具及验收、统一镜像离线包和容器环境真实部署、浏览器全流程、FinalShell虚拟机部署及整机重启。反向数据回填不等于生产路由回退已完成。未提交、未部署。


## 回退权威、离线包与容器/浏览器验收（2026-11-19）

反向回填在旧库同事务切换purchase_business_projections.authority=LEGACY，冲突时数据及权威均不变；真实MySQL专项通过。新增app.procurement.projection-enabled控制采购消费者，回退覆盖设置false，防止旧积压快照重新夺权；发布操作仍须停采购写入并排空两端消息。

五个Java服务Maven回归及打包退出0；前端pnpm type-check与pnpm build退出0（主JS包体积提示仍存在）。deploy/build-ms09-offline.ps1生成六个固定版本应用镜像及mysql/redis/rabbitmq离线tar，附build-manifest标明未提交工作区、Compose、环境模板、只读preflight及SHA256SUMS。离线tar为770252288字节，最终13文件校验通过。配置修复后重新复制runtime覆盖并更新校验清单，镜像未变。

本地独立项目erp-ms09-acceptance启动12个容器全部healthy；初始化随机密码临时老板账号。Playwright通过Nginx/Gateway登录、CSV预检与确认、采购详情点击确认下单；修复backend运行覆盖缺少RabbitMQ参数后，收货接口入库成功、库存预留2，采购收货回传RECEIVED且合格2。订单/采购/库存/收货详情/打包页面均加载，无pageerror；截图和JSON在output/ms09-container-browser，检查库存截图显示批次/预留/采购来源正常。恢复过程保留原失败记录，recovery-result.json记录修复后通过。

边界：店铺与旧后端员工展示使用隔离fixture准备，旧店铺初始化入口在独立Order模式不可用；未验证该入口。收货及部分创建由API执行，浏览器逐步点击全流程未全部覆盖；打包完整业务闭环已由ProcurementFulfillmentIT接口专项通过。虚拟机部署、整机重启和虚拟机回退未执行，MS-09仍进行中，Git未提交。


## VMware部署与整机恢复证据（2026-11-19，用户FinalShell执行）

VMware已导入ms09-20261119-candidate1；停写备份路径/opt/cross-border-erp/backups/ms09-20261119-161943。旧采购/收货为0，无PENDING认领；Order有3单。候选Backend由Flyway16迁至19，Procurement至4。空采购维护迁移六表均0并正常退出。逐个启动Order/Backend/Procurement/Gateway/Frontend后12服务healthy、保留订单3、HTTP200；用户确认重启前页面正常。

RabbitMQ原先在2核VM上诊断Erlang进程积压；沿原Compose增加compose.rabbitmq-runtime-fix.yml限制服务端及CLI调度线程，PIDS由1921降至21并恢复healthy。此覆盖须保留。

用户执行整机reboot后自动恢复迟缓：7分钟负载39.73，18分钟仍有Backend/RabbitMQ不健康；本次启动无OOM或自动重启记录。后续两者健康检查自然恢复成功。最终verify-ms09-vm-reboot.sh after退出通过（boot_id已变化、Docker/IP转发/镜像与重启策略/容器健康/HTTP校验通过），order.before/after及procurement.before/after均无diff。用户完整终端输出包含“整机恢复检查通过，数据计数与重启前一致”。

结论仅为整机最终恢复与计数保留通过；冷启动耗时过长仍为问题，不能称恢复时效达标。重启后浏览器业务检查尚待确认。未向虚拟机注入待处理采购消息，不能把空采购计数一致当作非空消息补偿验证。虚拟机反向回退演练仍待执行。


## VMware空数据回退路径确认（2026-11-19）

用户已保存回退前四库备份：/opt/cross-border-erp/backups/ms09-reverse-20261119-174306。演练时两侧供应商/采购/商品/分配均0，PENDING认领及采购/收货待发送事件均0。用户确认回退后订单、采购、库存、收货、打包页面均正常。

FinalShell证据：Gateway PROCUREMENT_SERVICE_URL为空；Backend APP_PROCUREMENT_PROJECTION_ENABLED=false；独立procurement为exited（143为停止时退出）；其余11服务healthy；Order保留3单。可确认空数据场景的旧采购路由恢复及可用性检查通过。反向维护任务完整退出日志未粘贴，不能将其作为新增数据反向回填的虚拟机证明；新增数据与权威事务回退已有本地MySQL专项。

下一步恢复MS-09正式路径并复核12服务及采购路由，避免最终环境留在回退状态。整项最终验收仍待恢复后确认；冷启动长耗时问题保留。


## 最终补充：MS-09恢复完成（2026-11-19）

用户完整终端输出确认两侧采购六表合计0后逐服务恢复MS-09，最终网关目标http://procurement:8083、12服务healthy、HTTP200；用户确认页面正常。空数据回退路径与恢复演练完成，当前不是回退模式。看板MS-09按当前数据规模部署验收完成，冷启动慢与虚拟机非空回退/带消息整机重启未覆盖的限制仍保留。完整证据与边界见ms09-vm-acceptance.md；前文“尚未部署/未重启”等为较早阶段记录。
