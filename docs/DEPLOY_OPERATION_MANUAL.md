# 项目打包部署操作手册

## 当前状态

`Campus Deploy #2` 已经运行成功，说明 GitHub Actions 可以完成：

```text
拉取代码 -> 后端打包 -> 后台前端打包 -> SSH 上传服务器 -> 更新服务 -> 重启后端 -> reload nginx
```

当前服务器：

```text
121.43.83.21
```

当前后端服务：

```text
campus-platform.service
```

当前后端端口：

```text
48080
```

## 日常开发流程

1. 本地修改代码。
2. 本地提交代码。

```powershell
git status
git add .
git commit -m "feat: 描述本次改动"
git push
```

3. 进入 GitHub 仓库 `Actions` 页面。
4. 查看 `Campus CI` 是否通过。

`Campus CI` 用来验证代码是否能正常打包，不会自动部署到服务器。

## 手动部署流程

进入：

```text
GitHub 仓库 -> Actions -> Campus Deploy
```

点击：

```text
Run workflow
```

选择：

```text
Branch: main
```

点击绿色按钮运行。

部署成功的判断标准：

```text
Campus Deploy 页面显示 Success
Build and deploy to server 显示绿色对勾
```

## 不要操作什么

如果之前有失败记录，不要直接在旧失败页面点击：

```text
Re-run jobs
```

旧失败页面可能会重新执行旧 commit 的脚本。

推荐回到：

```text
Actions -> Campus Deploy -> Run workflow
```

从最新 `main` 分支重新运行。

## 服务器验证命令

本机验证 SSH 是否能登录：

```powershell
ssh -i "$env:USERPROFILE\.ssh\campus_deploy_key" root@121.43.83.21
```

查看后端服务状态：

```bash
systemctl status campus-platform.service --no-pager -l
```

查看后端是否运行：

```bash
systemctl is-active campus-platform.service
```

查看 Redis：

```bash
redis-cli ping
```

返回：

```text
PONG
```

验证后端接口：

```bash
curl -i http://127.0.0.1:48080/admin-api/system/auth/get-permission-info
```

正常情况下，未登录会返回类似：

```json
{"code":401,"msg":"账号未登录","data":null}
```

这代表后端服务已经可访问。

## 服务器目录说明

后端 Jar：

```text
/opt/campus-platform/backend/app.jar
```

后端私有环境变量：

```text
/opt/campus-platform/backend/campus.env
```

后台前端静态文件：

```text
/opt/campus-platform/admin/dist
```

历史发布包：

```text
/opt/campus-platform/releases
```

后端日志：

```text
/opt/campus-platform/logs/backend.log
/opt/campus-platform/logs/backend-error.log
```

## 当前服务器基础环境

当前服务器已经准备：

```text
Java 17
Redis
Nginx
2G swap
campus-platform.service
```

2G swap 是为了避免小内存服务器启动 Java 后端时被系统 OOM 杀掉。

## GitHub Secrets

仓库部署依赖：

```text
SERVER_SSH_KEY
```

配置入口：

```text
Repository -> Settings -> Secrets and variables -> Actions
```

注意事项：

- 不要把 `SERVER_SSH_KEY` 写进代码。
- 不要把服务器密码写进代码。
- 不要把数据库密码写进代码。
- 数据库连接放在服务器 `/opt/campus-platform/backend/campus.env`。

## 常见问题

### Campus Deploy 显示 exit code 5

常见原因：

```text
campus-platform.service 不存在
systemctl reload nginx 失败
```

当前脚本已经兼容 nginx fallback：

```text
systemctl reload nginx 失败后，自动执行 nginx -s reload
```

### 后端启动后又退出

查看日志：

```bash
tail -200 /opt/campus-platform/logs/backend.log
```

常见原因：

```text
数据库连接错误
Redis 未启动
服务器内存不足
```

### Redis 连接失败

检查：

```bash
redis-cli ping
ss -lntp | grep 6379
```

启动：

```bash
systemctl enable --now redis
```

### 服务器内存不足

检查：

```bash
free -h
dmesg -T | grep -i oom
```

如果看到 Java 被 OOM kill，需要增加 swap 或升级服务器配置。

## 推荐权限策略

开发成员：

```text
GitHub 仓库 Write 权限
```

负责人：

```text
GitHub 仓库 Maintain 或 Admin 权限
```

部署建议：

```text
普通开发只提交代码
负责人手动运行 Campus Deploy
正式上线前确认 Campus CI 已通过
```

后续可以增加 `production` 环境审批，让部署必须经过负责人确认。

## 后续建议

1. 给服务器绑定正式域名。
2. 配置 HTTPS 证书。
3. 配置 nginx 反向代理后台和接口。
4. 把数据库、Redis、OSS 等生产配置整理成独立部署文档。
5. 增加企业微信或钉钉构建通知。
