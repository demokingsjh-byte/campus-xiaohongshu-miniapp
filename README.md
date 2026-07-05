# Campus Xiaohongshu Miniapp

[![Campus CI](https://github.com/demokingsjh-byte/campus-xiaohongshu-miniapp/actions/workflows/campus-ci.yml/badge.svg)](https://github.com/demokingsjh-byte/campus-xiaohongshu-miniapp/actions/workflows/campus-ci.yml)
[![Campus Release](https://github.com/demokingsjh-byte/campus-xiaohongshu-miniapp/actions/workflows/campus-release.yml/badge.svg)](https://github.com/demokingsjh-byte/campus-xiaohongshu-miniapp/actions/workflows/campus-release.yml)
[![Campus Deploy](https://github.com/demokingsjh-byte/campus-xiaohongshu-miniapp/actions/workflows/campus-deploy.yml/badge.svg)](https://github.com/demokingsjh-byte/campus-xiaohongshu-miniapp/actions/workflows/campus-deploy.yml)

区域校园综合服务小程序，定位为“按校区运营的小红书式校园生活平台”。项目当前已切换为成熟框架主线：

- `campus-platform`：基于 云点后台底座 / Yundian 的后台与服务端底座
- `campus-miniapp`：基于 Uni-app Vue3 / Vite / TypeScript 的小程序端

详细需求、模块边界、租户设计和排期见：

[docs/PROJECT_PLAN.md](docs/PROJECT_PLAN.md)

详细启动步骤和推荐工具见：

[docs/STARTUP_GUIDE.md](docs/STARTUP_GUIDE.md)

自动打包、版本发布和服务器更新方案见：

[docs/UPDATE_PIPELINE.md](docs/UPDATE_PIPELINE.md)

日常打包部署操作手册见：

[docs/DEPLOY_OPERATION_MANUAL.md](docs/DEPLOY_OPERATION_MANUAL.md)

## 当前目录

```text
campus-xiaohongshu-miniapp/
├── campus-platform/      # 后台与服务端，基于 云点后台底座
├── campus-miniapp/       # 小程序端，基于 Uni-app Vue3 模板
├── docs/                 # 项目文档
├── .gitignore
└── README.md
```

## 数据库初始化

先执行 云点 原始 SQL：

```text
campus-platform/sql/mysql/ruoyi-vue-pro.sql
```

再执行校园业务扩展 SQL：

```text
campus-platform/sql/mysql/campus-extension.sql
```

最后执行微信小程序初始化配置 SQL：

```text
campus-platform/sql/mysql/campus-wechat-miniapp-config.sql
```

如果需要启用阿里云 OSS 文件上传，再执行：

```text
campus-platform/sql/mysql/campus-oss-config.sql
```

## 小程序端

小程序端已增加校区租户能力：

- `campus-miniapp/src/utils/tenant.ts`
- `campus-miniapp/src/stores/modules/tenant.ts`
- 所有请求自动携带 `X-Tenant-Id` 和 `tenant-id`

验证命令：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-miniapp
npm install
npm run build:mp-weixin
```

## 后续主线

1. 初始化云数据库，执行 云点 SQL 和校园扩展 SQL。
2. 使用 云点 代码生成器生成 `campus_*` 后台 CRUD。
3. 完成区域、校区、代理人、分润规则后台管理。
4. 小程序端实现校区选择、登录、首页、发布闲置。
5. 进入交易、支付、分润、裂变闭环。
