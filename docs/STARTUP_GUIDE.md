# 项目启动指南

## 1. 项目目录

```text
D:\campus-xiaohongshu-miniapp
├── campus-platform   # 后台与服务端，基于 云点后台底座
├── campus-miniapp    # 微信小程序端，基于 Uni-app Vue3
└── docs              # 项目文档
```

## 2. 推荐工具

### 2.1 后台与服务端

推荐：

- IntelliJ IDEA Ultimate / Community
- JDK 8
- Maven 3.8+
- Redis
- DBeaver / Navicat / 阿里云 DMS

说明：

- 当前 云点后台底座 版本使用 Java 8。
- 如果本机没有 Maven，需要先安装 Maven，或者后续给项目补 Maven Wrapper。

### 2.2 小程序端

推荐：

- VS Code / WebStorm
- Node.js 20+ 或当前本机 Node 25
- npm
- 微信开发者工具

说明：

- 当前模板原本偏向 pnpm，但我已把主要脚本改成 npm 可直接运行。
- 如果后续团队统一使用 pnpm，也可以再恢复 pnpm 工作流。

### 2.3 数据库

推荐：

- 阿里云 DMS
- DBeaver
- Navicat

当前云数据库：

```text
数据库：SuperCampus
MySQL：5.7.44
```

数据库 SQL 已执行完成，当前包含 云点 基础表和校园扩展表。

## 3. 启动前检查

### 3.1 检查 Java

```powershell
java -version
```

期望是 Java 8。如果本机是 Java 17，也可能能编译部分模块，但 云点 当前配置是 Java 8，建议用 JDK 8 跑后台。

### 3.2 检查 Maven

```powershell
mvn -version
```

如果提示找不到 `mvn`，需要安装 Maven 并配置环境变量。

### 3.3 检查 Node

```powershell
node -v
npm -v
```

### 3.4 检查 Redis

当前后台配置的 Redis 是：

```text
127.0.0.1:6379
database: 0
```

如果本机没有 Redis，后台启动可能会报连接失败。

当前 Windows 已通过 winget 安装：

```text
taizod1024.redis-windows-fork
```

安装后新开的终端一般可以直接使用：

```powershell
redis-server --port 6379 --bind 127.0.0.1
```

验证：

```powershell
redis-cli -h 127.0.0.1 -p 6379 ping
```

返回：

```text
PONG
```

如果当前终端还找不到 `redis-server`，重开一个 PowerShell，或者到 WinGet 安装目录下执行 `redis-server.exe`。

## 4. 数据库配置

数据库配置文件：

```text
campus-platform/yudao-server/src/main/resources/application-local.yaml
campus-platform/yudao-server/src/main/resources/application-dev.yaml
```

数据库连接使用环境变量配置，避免把真实账号密码提交到代码仓库：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          url: ${CAMPUS_DB_URL:jdbc:mysql://127.0.0.1:3306/SuperCampus?...}
          username: ${CAMPUS_DB_USERNAME:root}
          password: ${CAMPUS_DB_PASSWORD:123456}
        slave:
          lazy: true
          url: ${CAMPUS_DB_SLAVE_URL:${CAMPUS_DB_URL:jdbc:mysql://127.0.0.1:3306/SuperCampus?...}}
          username: ${CAMPUS_DB_SLAVE_USERNAME:${CAMPUS_DB_USERNAME:root}}
          password: ${CAMPUS_DB_SLAVE_PASSWORD:${CAMPUS_DB_PASSWORD:123456}}
```

注意：

- 当前 RDS 不支持 SSL 握手，所以配置使用 `useSSL=false`。
- 本地连接云数据库时，在 PowerShell 里设置 `CAMPUS_DB_URL`、`CAMPUS_DB_USERNAME`、`CAMPUS_DB_PASSWORD` 后再启动后端。

## 5. 数据库初始化

初始化顺序：

```text
1. campus-platform/sql/mysql/ruoyi-vue-pro-cloud-nobom.sql
2. campus-platform/sql/mysql/campus-extension.sql
3. campus-platform/sql/mysql/campus-menu-prune.sql
4. campus-platform/sql/mysql/campus-menu.sql
5. campus-platform/sql/mysql/campus-school-data-upgrade.sql
```

说明：

- `ruoyi-vue-pro-cloud-nobom.sql` 是去除 BOM 和外键检查开关后的云数据库兼容版本。
- `campus-extension.sql` 是校园业务扩展表。
- `campus-menu-prune.sql` 是后台菜单精简脚本，只隐藏并禁用非 MVP 菜单，不物理删除。
- `campus-menu.sql` 是校园运营菜单和按钮权限。
- `campus-school-data-upgrade.sql` 会以幂等方式加入吉首大学和长沙学院。
- 已有数据库升级时执行 `campus-community-upgrade.sql`，创建社区发布和点赞收藏关系表；主分支自动部署会自动执行。
- 已存在乱码菜单时，先执行 `campus-menu-encoding-repair.sql`，再执行 `campus-menu.sql` 刷新校园菜单。
- 当前云数据库已经执行成功，一共 58 张表。

当前后台保留栏目：

```text
系统管理：租户管理、用户管理、角色管理、菜单管理、部门管理、字典管理、审计日志、地区管理
基础设施：代码生成、数据源配置、API 接口、文件管理、配置管理
```

校园扩展表包括：

```text
campus_agent
campus_commission_record
campus_commission_rule
campus_invite_relation
campus_product
campus_region
campus_school_catalog
campus_tenant_profile
campus_trade_order
campus_user_tenant
```

## 6. 启动后台服务端

### 6.1 用 IDEA 启动

推荐方式。

步骤：

1. 打开 IntelliJ IDEA。
2. 选择 `Open`。
3. 打开目录：

```text
D:\campus-xiaohongshu-miniapp\campus-platform
```

4. 等待 Maven 自动导入依赖。
5. 确认 Project SDK 使用 JDK 8。
6. 找到 `yudao-server` 模块里的启动类。
7. 选择 `local` 或 `dev` profile 启动。

常用启动参数：

```text
--spring.profiles.active=local
```

或：

```text
--spring.profiles.active=dev
```

后台服务端口：

```text
48080
```

启动后访问：

```text
http://localhost:48080
```

后台管理接口一般是：

```text
http://localhost:48080/admin-api
```

### 6.2 用命令行启动

前提：本机已安装 Maven。

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-platform
mvn clean install -DskipTests
```

然后启动 server：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-platform\yudao-server
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

如果 Maven 报 Java 版本问题，检查是否使用 JDK 8。

## 7. 启动小程序端

### 7.1 安装依赖

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-miniapp
npm install
```

### 7.2 开发模式

```powershell
npm run dev:mp-weixin
```

微信开发者工具导入：

```text
D:\campus-xiaohongshu-miniapp\campus-miniapp\dist\dev\mp-weixin
```

### 7.3 构建模式

```powershell
npm run build:mp-weixin
```

微信开发者工具导入：

```text
D:\campus-xiaohongshu-miniapp\campus-miniapp\dist\build\mp-weixin
```

### 7.4 H5 调试

```powershell
npm run dev:h5
```

## 8. 小程序接口地址

小程序环境配置在：

```text
campus-miniapp/env
```

如果需要修改后端接口地址，优先查看：

```text
campus-miniapp/src/utils/env
campus-miniapp/env/.env.development
campus-miniapp/env/.env.production
```

后端本地默认地址应指向：

```text
http://localhost:48080
```

小程序请求已自动携带：

```text
X-Tenant-Id
tenant-id
```

相关文件：

```text
campus-miniapp/src/utils/tenant.ts
campus-miniapp/src/stores/modules/tenant.ts
campus-miniapp/src/utils/http/index.ts
```

## 9. 常见问题

### 9.1 Maven 找不到

报错：

```text
mvn : The term 'mvn' is not recognized
```

处理：

- 安装 Maven。
- 配置 `MAVEN_HOME`。
- 把 `%MAVEN_HOME%\bin` 加入 PATH。

### 9.2 Java 版本不对

云点 当前配置：

```text
java.version = 1.8
```

建议用 JDK 8。

### 9.3 Redis 连接失败

确认 Redis 是否启动：

```text
127.0.0.1:6379
```

如果你不想本地装 Redis，可以后续改成云 Redis。

### 9.4 MySQL SSL 报错

之前连接云数据库时出现过：

```text
Server does not support secure connection
```

所以当前 JDBC 配置使用：

```text
useSSL=false
```

不要改回 `useSSL=true`。

### 9.5 SQL 文件执行报 1064

原因可能是：

- SQL 文件带 UTF-8 BOM。
- DB 工具不支持多语句。
- 普通查询窗口不适合执行大 SQL 文件。

建议使用：

```text
ruoyi-vue-pro-cloud-nobom.sql
```

并通过 DMS / Navicat / DBeaver 的“导入 SQL 文件”执行。

## 10. 推荐启动顺序

第一次完整启动建议按这个顺序：

1. 确认云数据库已初始化。
2. 启动本地 Redis。
3. 用 IDEA 打开 `campus-platform`。
4. 使用 `local` profile 启动后端。
5. 用 VS Code 打开 `campus-miniapp`。
6. 执行 `npm install`。
7. 执行 `npm run dev:mp-weixin`。
8. 用微信开发者工具导入小程序产物目录。

## 11. 当前状态

- 云数据库 SQL 已执行成功。
- 小程序 `npm run build:mp-weixin` 已验证通过。
- Redis 已安装并启动验证，`127.0.0.1:6379` 返回 `PONG`。
- 后台服务端还未在本机完整启动验证，因为当前机器缺少 Maven，且需要确认 JDK 8。
