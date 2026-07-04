# 小程序到后端登录闭环说明

## 已完成闭环

本次完成了校园小程序的微信静默登录闭环：

1. 小程序启动后调用 `uni.login` 获取微信临时 `code`。
2. 小程序请求后端 `POST /app-api/campus/auth/wechat-login`。
3. 后端通过系统模块的微信小程序社交能力换取 `openid`。
4. 后端按 `openid` 创建或更新 `campus_miniapp_user`。
5. 后端创建 OAuth2 访问令牌，返回 `token`、`refreshToken` 和用户信息。
6. 小程序保存 token，后续请求自动带 `Authorization: Bearer <token>`。
7. 小程序可调用 `/me`、`/profile`、`/phone` 完成用户资料读取、资料补全和手机号授权绑定。

## 后端接口

| 方法 | 地址 | 鉴权 | 用途 |
| --- | --- | --- | --- |
| POST | `/app-api/campus/auth/wechat-login` | 不需要 | 微信小程序静默登录，返回 token 和用户信息 |
| GET | `/app-api/campus/auth/me` | 需要 | 获取当前登录的校园小程序用户 |
| PUT | `/app-api/campus/auth/profile` | 需要 | 补全昵称、头像、学校、校区、身份类型 |
| POST | `/app-api/campus/auth/phone` | 需要 | 使用微信授权的 `phoneCode` 绑定手机号 |

## 小程序端接入点

- `src/services/api/auth.ts`：新增校园小程序认证接口。
- `src/stores/modules/user.ts`：启动时自动尝试微信静默登录；mock/H5 环境使用 mock code 走同一套流程。
- `src/utils/http/index.ts`：修复 `ignoreAuth` 判断，并兼容后端 `CommonResult.code = 0`。
- `src/mock/v1/modules/auth.ts`：补齐登录、当前用户、资料更新、手机号绑定 mock。

## 数据表

新增表：`campus_miniapp_user`

核心字段：

- `openid`：微信小程序用户唯一标识。
- `unionid`：微信开放平台 unionid，预留。
- `nickname`、`avatar`：用户公开资料。
- `mobile`、`phone_country_code`：用户主动授权后的手机号信息。
- `school_name`、`campus_name`、`role_type`：校园业务资料。
- `source_scene`、`inviter_user_id`：入口场景和邀请关系预留。
- `first_login_time`、`last_login_time`：登录时间轨迹。
- `tenant_id`：所属校区租户。

## 信息收集边界

当前无感登录只能稳定拿到 `openid`，用于识别同一个微信小程序用户。

手机号、昵称、头像、学校、校区、身份类型都不应该强行无感采集：

- 手机号需要用户点击微信授权按钮后，用 `phoneCode` 换取。
- 昵称和头像建议在用户发布、联系、报名等关键动作前引导补全。
- 学校和校区建议通过首页校区选择、定位辅助或邀请链接 scene 带入。

## 明天开发计划

1. 首页接入真实登录态：显示当前用户头像、昵称、校区和手机号绑定状态。
2. 做校区选择页：选择后写入本地 tenant，并让后续请求自动带租户头。
3. 做手机号授权按钮：在需要联系、发布、下单等动作前触发绑定。
4. 将邀请 scene 解析接入分享链路：从 `scene` 写入 `inviterUserId` 或邀请码。
5. 后端增加最小接口测试：覆盖首次登录、重复登录、资料更新、手机号绑定。
6. 接入微信小程序真实 appId/appSecret 配置，并在测试号上联调一次完整链路。

