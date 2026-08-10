# 项目本机手动运行指南

本文档适用于在 Windows PowerShell 中手动运行校园小红书项目，包含后端服务、管理后台、H5 和微信小程序。

最后核对日期：2026-08-10。

## 1. 项目组成与本机地址

| 模块 | 目录 | 启动后的地址或产物 |
| --- | --- | --- |
| 小程序 H5 | `campus-miniapp` | <http://localhost:3000> |
| 微信小程序 | `campus-miniapp` | `campus-miniapp/dist/dev/mp-weixin` |
| 管理后台 | `campus-platform/yudao-ui/yudao-ui-admin-vue3-full` | <http://localhost:8080> |
| 后端服务 | `campus-platform/yudao-server` | <http://localhost:48080> |
| 管理端接口 | 后端服务提供 | <http://localhost:48080/admin-api> |
| 小程序接口 | 后端服务提供 | <http://localhost:48080/app-api> |
| Swagger UI | 后端服务提供 | <http://localhost:48080/swagger-ui> |

> 微信小程序不是通过浏览器地址访问。启动编译后，需要使用微信开发者工具导入产物目录。

## 2. 运行方式选择

### 2.1 只预览小程序页面

只启动 H5 即可。项目当前的开发环境配置默认请求线上接口，不要求本机同时启动 Java 后端。

直接执行本文档第 7 节。

### 2.2 完整本地联调

建议依次启动：

1. MySQL；
2. Redis；
3. Java 后端；
4. 管理后台；
5. H5 或微信小程序。

每个长期运行的服务单独使用一个 PowerShell 窗口。看到启动日志后不要关闭对应窗口。

## 3. 环境准备

### 3.1 必需软件

| 软件 | 建议版本 | 用途 |
| --- | --- | --- |
| JDK | 8 或 17 | 运行 Spring Boot 2.7 后端；项目编译目标为 Java 8 |
| Node.js | 20.19 或更高 | 运行前端项目 |
| npm | Node.js 自带 | 小程序端依赖管理 |
| pnpm | 8.6 或更高 | 管理后台依赖管理 |
| MySQL | 5.7 | 本地数据库 |
| Redis | 可兼容 Redis 5+ 的版本 | 缓存与登录状态 |
| 微信开发者工具 | 当前稳定版 | 运行微信小程序 |

项目已内置 Maven 3.9.9 和 `mvnw.cmd`，不需要另外安装 Maven。

### 3.2 检查环境

打开 PowerShell，执行：

```powershell
java -version
node -v
npm -v
pnpm -v
```

如果找不到 `pnpm`，执行：

```powershell
npm install -g pnpm
```

如果电脑安装了多个 JDK，请确认当前终端的 `java -version` 显示 Java 8 或 17。当前机器已确认 Maven Wrapper 可使用 JDK 17。

## 4. 更新代码

进入项目根目录：

```powershell
cd D:\campus-xiaohongshu-miniapp
git status
git pull --ff-only
```

如果 `git status` 显示自己修改过的文件，先提交或暂存这些修改，再拉取代码，避免覆盖本地工作。

## 5. 准备 MySQL

本地后端默认连接：

```text
地址：127.0.0.1:3306
数据库：SuperCampus
用户名：root
```

首次搭建数据库时，按项目实际需要执行 `campus-platform/sql/mysql` 下的初始化和升级 SQL。基础初始化顺序可参考：

```text
1. ruoyi-vue-pro-cloud-nobom.sql
2. campus-extension.sql
3. campus-menu-prune.sql
4. campus-menu.sql
5. campus-school-data-upgrade.sql
```

数据库已有数据时，不要重复执行破坏性初始化脚本；只执行尚未应用的升级脚本。

后端启动前，可在当前 PowerShell 窗口设置数据库连接。请将示例密码换成自己的密码：

```powershell
$env:CAMPUS_DB_URL='jdbc:mysql://127.0.0.1:3306/SuperCampus?allowMultiQueries=true&useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai&autoReconnect=true&rewriteBatchedStatements=true'
$env:CAMPUS_DB_USERNAME='root'
$env:CAMPUS_DB_PASSWORD='<你的数据库密码>'
```

这些环境变量只在当前 PowerShell 窗口有效，不会写入代码仓库。

## 6. 启动 Redis 和后端

### 6.1 启动 Redis

打开一个新的 PowerShell 窗口：

```powershell
redis-server --port 6379 --bind 127.0.0.1
```

再打开另一个窗口验证：

```powershell
redis-cli -h 127.0.0.1 -p 6379 ping
```

返回 `PONG` 表示 Redis 正常。

### 6.2 首次编译后端

打开新的 PowerShell 窗口，并先按第 5 节设置数据库环境变量，然后执行：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-platform
.\mvnw.cmd clean install -DskipTests
```

首次编译需要下载依赖，耗时会比后续编译长。

### 6.3 命令行启动后端

编译成功后，在同一窗口执行：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-platform\yudao-server
..\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

看到服务监听 `48080` 且没有报错后，访问：

```text
http://localhost:48080
http://localhost:48080/swagger-ui
```

### 6.4 使用 IDEA 启动后端

也可以使用 IntelliJ IDEA：

1. 打开 `D:\campus-xiaohongshu-miniapp\campus-platform`；
2. 将 Project SDK 设置为 JDK 8 或 JDK 17；
3. 等待 Maven 项目导入完成；
4. 找到 `cn.iocoder.yudao.server.YudaoServerApplication`；
5. 运行参数设置为 `--spring.profiles.active=local`；
6. 在同一个运行配置里填写第 5 节的三个数据库环境变量；
7. 运行启动类。

## 7. 启动小程序 H5

打开新的 PowerShell 窗口：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-miniapp
npm install
npm run dev:h5
```

浏览器访问：

```text
http://localhost:3000
```

`npm install` 首次运行、`package.json` 或 `package-lock.json` 更新后需要执行；其他时候可以直接执行 `npm run dev:h5`。

### 7.1 H5 连接本机后端

当前 H5 开发配置默认请求线上接口。需要连接本机后端时，在启动 H5 的 PowerShell 窗口先执行：

```powershell
$env:VITE_BASE_URL='http://localhost:48080/app-api'
npm run dev:h5
```

停止服务后按 `Ctrl+C`。关闭该 PowerShell 窗口后，临时环境变量会自动失效。

## 8. 启动微信小程序

打开新的 PowerShell 窗口：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-miniapp
npm install
npm run dev:mp-weixin
```

保持命令窗口运行，然后在微信开发者工具中导入：

```text
D:\campus-xiaohongshu-miniapp\campus-miniapp\dist\dev\mp-weixin
```

需要连接本机后端时，先在同一窗口设置接口地址，再启动编译：

```powershell
$env:VITE_BASE_URL='http://localhost:48080/app-api'
npm run dev:mp-weixin
```

微信开发者工具访问本机接口时还需注意：

- 开发调试阶段，可在“详情 → 本地设置”中按需关闭合法域名校验；
- 真机无法使用手机自身的 `localhost` 访问电脑；真机调试时应把地址改成电脑的局域网 IPv4 地址，例如 `http://192.168.8.100:48080/app-api`；
- 电脑和手机需要连接同一个局域网，并允许 Windows 防火墙放行后端端口。

查看电脑局域网地址：

```powershell
ipconfig
```

### 8.1 导入正式构建产物

需要验证与发布环境一致的压缩构建时，执行：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-miniapp
npm run build:mp-weixin
```

构建成功后，在微信开发者工具中导入：

```text
D:\campus-xiaohongshu-miniapp\campus-miniapp\dist\build\mp-weixin
```

开发时使用 `dist\dev\mp-weixin`，正式构建检查时使用 `dist\build\mp-weixin`，不要混用两个目录。

## 9. 启动管理后台

先确认本机后端已经启动，再打开新的 PowerShell 窗口：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-platform\yudao-ui\yudao-ui-admin-vue3-full
pnpm install
pnpm dev
```

浏览器访问：

```text
http://localhost:8080
```

本地模式已配置为请求：

```text
http://localhost:48080/admin-api
```

## 10. 推荐的完整启动顺序

完整联调时建议保留四个 PowerShell 窗口：

| 窗口 | 运行内容 | 成功标志 |
| --- | --- | --- |
| 1 | Redis | `redis-cli ping` 返回 `PONG` |
| 2 | Java 后端 | `48080` 端口开始监听 |
| 3 | 管理后台 | 能打开 <http://localhost:8080> |
| 4 | H5 或微信小程序编译 | 能打开 `3000` 或微信开发者工具正常编译 |

推荐顺序：Redis → 后端 → 管理后台 → H5/微信小程序。

## 11. 停止服务

在对应服务的 PowerShell 窗口按：

```text
Ctrl+C
```

如果端口仍被占用，查看占用进程：

```powershell
Get-NetTCPConnection -LocalPort 3000,8080,48080 -ErrorAction SilentlyContinue |
  Select-Object LocalPort,State,OwningProcess
```

确认进程确实属于本项目后，再通过任务管理器结束，不要直接终止不明进程。

## 12. 常见问题

### 12.1 `java` 找不到或版本不正确

安装 JDK 8 或 JDK 17，并检查 `JAVA_HOME` 和 `Path`。修改环境变量后应重新打开 PowerShell。

### 12.2 后端提示数据库连接失败

依次检查：

1. MySQL 是否运行；
2. `SuperCampus` 数据库是否存在；
3. 当前窗口的 `CAMPUS_DB_URL`、`CAMPUS_DB_USERNAME`、`CAMPUS_DB_PASSWORD` 是否正确；
4. JDBC 地址是否保持 `useSSL=false`。

### 12.3 后端提示 Redis 连接失败

执行：

```powershell
redis-cli -h 127.0.0.1 -p 6379 ping
```

没有返回 `PONG` 时，先启动 Redis。

### 12.4 `pnpm` 找不到

执行：

```powershell
npm install -g pnpm
```

然后重新打开 PowerShell。

### 12.5 `3000`、`8080` 或 `48080` 被占用

先按第 11 节查询占用进程。也可以临时修改对应环境变量或配置文件中的端口，但前后端地址需要保持一致。

### 12.6 前端能打开，但接口请求失败

检查浏览器开发者工具的 Network：

- H5 默认走线上接口；本地联调时需设置 `VITE_BASE_URL=http://localhost:48080/app-api`；
- 管理后台本地模式默认走 `http://localhost:48080/admin-api`；
- 确认后端 `48080` 已启动；
- 真机调试不能使用 `localhost` 访问电脑。

### 12.7 拉取代码时报 SSH 公钥错误

确认 GitHub SSH 密钥已经加载或在 Git 配置中指定正确的私钥。不要把私钥文件提交到仓库。

## 13. 最短启动命令速查

只运行 H5：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-miniapp
npm run dev:h5
```

只运行微信小程序编译：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-miniapp
npm run dev:mp-weixin
```

只运行管理后台：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-platform\yudao-ui\yudao-ui-admin-vue3-full
pnpm dev
```

只运行后端：

```powershell
cd D:\campus-xiaohongshu-miniapp\campus-platform\yudao-server
..\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```
