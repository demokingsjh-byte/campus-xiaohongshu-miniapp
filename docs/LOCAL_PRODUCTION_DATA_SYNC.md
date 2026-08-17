# 本地真实数据同步

本地开发环境不会因为拉取 Git 代码而自动获得线上数据库数据。项目通过只读线上公开接口，将首页配置、真实帖子、作者、图片、评论和回复单向同步到本地 MySQL。

## 自动同步

运行微信小程序开发命令时，同步会在编译前自动执行：

```powershell
cd campus-miniapp
npm run dev:mp-weixin
```

数据流向固定为：

```text
线上公开接口（只读） -> 本地 MySQL（写入）
                    -> 本地文件存储（图片镜像）
```

脚本不会连接线上数据库，也不会把本地发布、修改或删除的数据写回线上。

## 手动同步

```powershell
cd campus-miniapp
npm run sync:production-data
```

也可以在仓库根目录直接运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/sync-production-data.ps1`。

默认同步吉首大学租户 `201`。如需同步其他租户：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/sync-production-data.ps1 -TenantId 202
```

## 同步规则

- 搜索提示、公告、分类导航和分类显示开关会保存为租户级首页配置。
- 线上作者在本地使用 `prod-sync-{tenantId}-{userId}` 作为同步标识。
- 线上帖子在本地使用 `prod-sync:{postId}` 作为同步标识。
- 线上评论在本地使用 `prod-sync-comment:{commentId}` 作为同步标识，并在写入后恢复父评论关系。
- 重复执行会更新现有同步记录，不会重复插入。
- 本地自行发布的帖子不会被覆盖或删除。
- 线上不再返回的同步帖子会在本地软删除。
- 线上图片会在同步时下载到本地主文件存储目录，数据库保存永久的本地访问地址。
- 图片以 OSS 对象路径作为本地相对路径；重复同步会复用已经完整下载的文件。
- 任意图片下载失败时，数据库事务不会执行，避免写入部分更新或过期签名地址。

## 不同步的数据

手机号、登录凭证、私信、通知、订单、支付记录，以及某个用户自己的点赞、收藏状态不属于公开数据，不会复制到本地。它们应继续由本地测试账号独立产生。

自动同步发生在每次执行 `npm run dev:mp-weixin` 之前。开发进程长期不重启时，如需获取线上刚发布的内容，请单独执行 `npm run sync:production-data`，然后在微信开发者工具中点击“编译”。

## 前提

- Docker Desktop 已启动。
- `campus-mysql` 容器正在运行。
- 本地数据库名称为 `SuperCampus`。
- 主文件存储配置必须是本地存储（存储类型 `10`），且配置了可写的 `basePath` 和本地域名。
- 同步脚本不会保存数据库密码；运行前必须通过环境变量 `CAMPUS_LOCAL_DB_PASSWORD` 提供本地 MySQL 密码。
