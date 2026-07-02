# 校园项目接入 云点后台底座 初步改造说明

## 源码来源

- 后台底座：`https://github.com/YunaiV/ruoyi-vue-pro.git`
- 已整理为：`campus-platform`

## 改造原则

1. 复用 云点后台底座 的 `system_tenant` 做 SaaS 租户底座。
2. 校区不是单独再造租户中间件，而是给 `system_tenant` 增加校园业务资料。
3. 所有校园业务表都带 `tenant_id`，直接复用 云点 的租户拦截、数据隔离、权限体系。
4. 校区代理、分润、裂变做成 `campus_*` 业务扩展表。

## 已新增文件

- `sql/mysql/campus-extension.sql`

这个 SQL 应在 云点 原始 `sql/mysql/ruoyi-vue-pro.sql` 之后执行。

## 后台菜单建议

在 云点 后台新增一级菜单：`校园运营`

建议子菜单：

- 区域管理：`campus_region`
- 学校资料：`campus_school_catalog`
- 校区租户资料：`campus_tenant_profile`
- 校区代理：`campus_agent`
- 邀请裂变：`campus_invite_relation`
- 闲置商品：`campus_product`
- 交易订单：`campus_trade_order`
- 分润规则：`campus_commission_rule`
- 分润记录：`campus_commission_record`

## 下一步

1. 初始化 云点 数据库。
2. 执行 `campus-extension.sql`。
3. 使用 云点 代码生成器生成 `campus_*` 表的 CRUD。
4. 对 `campus_tenant_profile` 增加创建校区时同步创建/绑定 `system_tenant` 的业务逻辑。
5. 对小程序端统一添加 `X-Tenant-Id` 请求头。
