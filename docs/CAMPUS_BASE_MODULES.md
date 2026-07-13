# 校园基础模块说明

## 当前完成范围

本阶段先把后台可配置、可录入的基础模块打通，目标是让后续小程序 API、分账分润、裂变邀请都能落在稳定的数据结构上。

已完成后台基础资源：

- 区域管理：`campus_region`
- 学校资料：`campus_school_catalog`
- 校区租户：`campus_tenant_profile`
- 校区代理：`campus_agent`
- 商品管理：`campus_product`
- 内容管理：`campus_post`

已预留业务数据表：

- 用户校区关系：`campus_user_tenant`
- 邀请裂变关系：`campus_invite_relation`
- 交易订单：`campus_trade_order`
- 分润规则：`campus_commission_rule`
- 分润记录：`campus_commission_record`
- 社区发布：`campus_post`
- 点赞收藏关系：`campus_post_interaction`

## 设计重点

### 校区租户

系统底层继续复用 Yundian / 云点 的 `system_tenant` 作为 SaaS 租户基础。校园业务自己的校区资料放在 `campus_tenant_profile`。

这样做的好处是：

- 后台权限、租户隔离、用户体系可以直接复用成熟框架能力。
- 校区自己的学校、地址、代理人、分润开关等业务信息独立存储。
- 后期接入分账、代理分润、邀请裂变时，可以统一按 `system_tenant_id` 和 `tenant_id` 归属。

### 校区代理

代理人数据放在 `campus_agent`，通过 `system_tenant_id` 绑定校区，通过 `user_id` 绑定用户。

目前字段已预留：

- `agent_level`：代理等级，后续可扩展主代理、副代理、推广员。
- `commission_rate`：代理分润比例。
- `invite_code`：代理邀请码，给裂变邀请使用。
- `started_at` / `ended_at`：代理生效和终止时间。

### 商品

商品基础表是 `campus_product`，现在先打通后台 CRUD。小程序端发布、审核、上下架、交易订单后续基于这个表继续扩展。

## 后台菜单

菜单 SQL 在：

`campus-platform/sql/mysql/campus-menu.sql`

执行后后台会新增一级菜单：

- 校园运营

包含以下页面：

- 区域管理
- 学校资料
- 校区租户
- 校区代理
- 商品管理

如果登录后看不到新菜单，先退出重新登录；普通角色还需要在角色管理中分配对应菜单权限。

## 后端接口

后端模块路径：

`campus-platform/yudao-module-campus`

接口统一走后台管理端：

- `POST /admin-api/campus/{resource}/create`
- `PUT /admin-api/campus/{resource}/update`
- `DELETE /admin-api/campus/{resource}/delete?id=1`
- `DELETE /admin-api/campus/{resource}/delete-list`
- `GET /admin-api/campus/{resource}/get?id=1`
- `GET /admin-api/campus/{resource}/page?pageNo=1&pageSize=10`

当前支持的 `resource`：

- `region`
- `school`
- `tenant-profile`
- `agent`
- `product`
- `post`

接口做了资源和字段白名单，避免外部传入任意表名或任意字段。

## 前端页面

后台 Vue3 页面路径：

`campus-platform/yudao-ui/yudao-ui-admin-vue3-full/src/views/campus/base/index.vue`

API 封装路径：

`campus-platform/yudao-ui/yudao-ui-admin-vue3-full/src/api/campus/base/index.ts`

当前先用一个通用页面承接五个基础资源，便于快速进入可用状态。后面业务规则稳定后，可以再拆成更精细的专用页面。

## SQL 执行顺序

新环境建议顺序：

1. `campus-platform/sql/mysql/ruoyi-vue-pro-cloud-nobom.sql`
2. `campus-platform/sql/mysql/campus-extension.sql`
3. `campus-platform/sql/mysql/campus-menu-prune.sql`
4. `campus-platform/sql/mysql/campus-menu.sql`
5. `campus-platform/sql/mysql/campus-school-data-upgrade.sql`

已有数据库升级时，额外执行 `campus-platform/sql/mysql/campus-community-upgrade.sql`；GitHub 主分支自动部署会先备份相关表并自动执行该迁移。

已存在乱码菜单的数据库，先执行 `campus-menu-encoding-repair.sql`，再重新执行 `campus-menu.sql`。

## 验证命令

后端编译：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-platform
.\mvnw.cmd -pl yudao-server -am install -DskipTests
```

后台前端打包：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-platform\yudao-ui\yudao-ui-admin-vue3-full
pnpm build:local
```

## 下一步建议

1. 把通用 CRUD 升级为正式业务 Service + Mapper，开始补领域校验。
2. 校区租户创建时联动 `system_tenant`，避免手动填 `system_tenant_id`。
3. 代理人选择改成后台用户/会员用户弹窗，不再手填 `user_id`。
4. 商品模块补分类、图片上传、审核流和上下架状态。
5. 小程序端补校区选择、首页商品列表、商品详情、发布商品接口。
