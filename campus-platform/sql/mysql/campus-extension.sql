-- Campus business extension for RuoYi-Vue-Pro / Yudao.
-- Run after sql/mysql/ruoyi-vue-pro.sql.
-- RuoYi's system_tenant remains the SaaS tenant foundation.
-- campus_tenant_profiles stores campus-specific operating data for each system tenant.

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `campus_commission_record`;
DROP TABLE IF EXISTS `campus_commission_rule`;
DROP TABLE IF EXISTS `campus_trade_order`;
DROP TABLE IF EXISTS `campus_product`;
DROP TABLE IF EXISTS `campus_invite_relation`;
DROP TABLE IF EXISTS `campus_user_tenant`;
DROP TABLE IF EXISTS `campus_post_interaction`;
DROP TABLE IF EXISTS `campus_post`;
DROP TABLE IF EXISTS `campus_user_follow`;
DROP TABLE IF EXISTS `campus_miniapp_user`;
DROP TABLE IF EXISTS `campus_agent`;
DROP TABLE IF EXISTS `campus_tenant_profile`;
DROP TABLE IF EXISTS `campus_region`;
DROP TABLE IF EXISTS `campus_school_catalog`;
DROP TABLE IF EXISTS `campus_home_category`;

CREATE TABLE `campus_region` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '区域编号',
  `name` varchar(100) NOT NULL COMMENT '区域名称，如杭州大学城',
  `province` varchar(50) NOT NULL DEFAULT '' COMMENT '省份',
  `city` varchar(50) NOT NULL DEFAULT '' COMMENT '城市',
  `district` varchar(50) NOT NULL DEFAULT '' COMMENT '区县',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0开启 1关闭',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，平台级表默认0',
  PRIMARY KEY (`id`),
  KEY `idx_city` (`city`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园区域';

CREATE TABLE `campus_school_catalog` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '学校资料编号',
  `name` varchar(100) NOT NULL COMMENT '学校名称',
  `province` varchar(50) NOT NULL DEFAULT '' COMMENT '省份',
  `city` varchar(50) NOT NULL DEFAULT '' COMMENT '城市',
  `district` varchar(50) NOT NULL DEFAULT '' COMMENT '区县',
  `logo_url` varchar(255) NOT NULL DEFAULT '' COMMENT '学校Logo',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0开启 1关闭',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，平台级表默认0',
  PRIMARY KEY (`id`),
  KEY `idx_city` (`city`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园学校资料';

CREATE TABLE `campus_home_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类编号',
  `category_key` varchar(50) NOT NULL COMMENT '稳定分类标识',
  `title` varchar(50) NOT NULL COMMENT '小程序显示名称',
  `channel` varchar(50) NOT NULL COMMENT '对应内容频道',
  `icon_url` varchar(500) NOT NULL DEFAULT '' COMMENT '分类图标地址',
  `publish_type` varchar(30) NOT NULL DEFAULT '' COMMENT '发布内容类型',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `icon_visible` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否显示图标',
  `title_visible` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否显示名称',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序，数值越小越靠前',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '校区租户编号，0表示全局',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_category` (`tenant_id`, `category_key`),
  KEY `idx_tenant_enabled_sort` (`tenant_id`, `enabled`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小程序首页分类入口配置';

INSERT INTO `campus_home_category`
(`category_key`, `title`, `channel`, `icon_url`, `publish_type`, `enabled`, `icon_visible`, `title_visible`, `sort`, `creator`, `updater`, `tenant_id`)
VALUES
('recommend', '推荐', '推荐', '/static/images/home-prototype/category-recommend.png', '', b'1', b'1', b'1', 10, 'campus', 'campus', 0),
('idle', '二手闲置', '二手', '/static/images/home-prototype/category-idle.png', 'idle', b'1', b'1', b'1', 20, 'campus', 'campus', 0),
('errand', '代拿代办', '互助', '/static/images/home-prototype/category-errand.png', 'help', b'1', b'1', b'1', 30, 'campus', 'campus', 0),
('fun', '校园趣事', '社团', '/static/images/home-prototype/category-fun.png', 'club', b'1', b'1', b'1', 40, 'campus', 'campus', 0),
('job', '兼职信息', '兼职', '/static/images/home-prototype/category-job.png', 'job', b'1', b'1', b'1', 50, 'campus', 'campus', 0),
('confession', '表白墙', '表白', '/static/images/home-prototype/category-confession.png', 'confession', b'1', b'1', b'1', 60, 'campus', 'campus', 0),
('groupbuy', '商家团购', '探店', '/static/images/home-prototype/category-groupbuy.png', 'shop', b'1', b'1', b'1', 70, 'campus', 'campus', 0);

CREATE TABLE `campus_tenant_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '校区资料编号',
  `system_tenant_id` bigint NOT NULL COMMENT '关联 system_tenant.id',
  `region_id` bigint NOT NULL DEFAULT 0 COMMENT '区域编号',
  `school_id` bigint NOT NULL DEFAULT 0 COMMENT '学校资料编号',
  `school_name` varchar(100) NOT NULL COMMENT '学校名称快照',
  `campus_name` varchar(100) NOT NULL DEFAULT '' COMMENT '校区名称',
  `display_name` varchar(150) NOT NULL COMMENT '小程序展示名称',
  `logo_url` varchar(255) NOT NULL DEFAULT '' COMMENT '校区Logo',
  `province` varchar(50) NOT NULL DEFAULT '' COMMENT '省份',
  `city` varchar(50) NOT NULL DEFAULT '' COMMENT '城市',
  `district` varchar(50) NOT NULL DEFAULT '' COMMENT '区县',
  `address` varchar(255) NOT NULL DEFAULT '' COMMENT '详细地址',
  `lat` decimal(10,7) DEFAULT NULL COMMENT '纬度',
  `lng` decimal(10,7) DEFAULT NULL COMMENT '经度',
  `agent_user_id` bigint NOT NULL DEFAULT 0 COMMENT '当前主代理用户编号',
  `commission_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否参与分润',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待开通 1运营中 2暂停 3关闭',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，等同 system_tenant_id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_tenant` (`system_tenant_id`),
  KEY `idx_region` (`region_id`),
  KEY `idx_school` (`school_id`),
  KEY `idx_city_status` (`city`, `status`),
  KEY `idx_agent_user` (`agent_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园校区租户资料';

CREATE TABLE `campus_agent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '代理编号',
  `system_tenant_id` bigint NOT NULL COMMENT '关联 system_tenant.id',
  `user_id` bigint NOT NULL COMMENT '代理用户编号，可关联 member_user 或 system_user',
  `agent_level` tinyint NOT NULL DEFAULT 1 COMMENT '代理等级：1主代理 2副代理 3推广员',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1生效 2暂停 3终止',
  `commission_rate` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '代理分润比例，单位百分比',
  `invite_code` varchar(32) NOT NULL COMMENT '代理邀请码',
  `started_at` datetime DEFAULT NULL COMMENT '生效时间',
  `ended_at` datetime DEFAULT NULL COMMENT '终止时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，等同 system_tenant_id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  UNIQUE KEY `uk_tenant_user_level` (`system_tenant_id`, `user_id`, `agent_level`),
  KEY `idx_tenant_status` (`system_tenant_id`, `status`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园代理';

CREATE TABLE `campus_user_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户校区关系编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `system_tenant_id` bigint NOT NULL COMMENT '关联 system_tenant.id',
  `relation_type` tinyint NOT NULL DEFAULT 1 COMMENT '关系类型：1默认校区 2认证校区 3运营身份校区',
  `defaulted` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否默认校区',
  `source` tinyint NOT NULL DEFAULT 1 COMMENT '来源：1用户选择 2邀请进入 3后台分配 4学生认证',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，等同 system_tenant_id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tenant_type` (`user_id`, `system_tenant_id`, `relation_type`),
  KEY `idx_user_default` (`user_id`, `defaulted`),
  KEY `idx_system_tenant` (`system_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园用户校区关系';

CREATE TABLE `campus_miniapp_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户编号',
  `openid` varchar(64) NOT NULL COMMENT '微信小程序 openid',
  `unionid` varchar(64) NOT NULL DEFAULT '' COMMENT '微信开放平台 unionid',
  `nickname` varchar(64) NOT NULL DEFAULT '' COMMENT '昵称',
  `avatar` varchar(512) NOT NULL DEFAULT '' COMMENT '头像',
  `mobile` varchar(32) NOT NULL DEFAULT '' COMMENT '手机号',
  `phone_country_code` varchar(8) NOT NULL DEFAULT '' COMMENT '手机号区号',
  `school_name` varchar(100) NOT NULL DEFAULT '' COMMENT '学校名称',
  `campus_name` varchar(100) NOT NULL DEFAULT '' COMMENT '校区名称',
  `grade` varchar(32) NOT NULL DEFAULT '' COMMENT '年级',
  `gender` varchar(16) NOT NULL DEFAULT '不公开' COMMENT '性别：不公开、男、女',
  `role_type` varchar(32) NOT NULL DEFAULT 'student' COMMENT '身份类型：student、merchant、agent',
  `source_scene` varchar(128) NOT NULL DEFAULT '' COMMENT '入口 scene',
  `inviter_user_id` bigint DEFAULT NULL COMMENT '邀请人用户编号',
  `first_login_time` datetime NOT NULL COMMENT '首次登录时间',
  `last_login_time` datetime NOT NULL COMMENT '最近登录时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，等同校区租户',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_mobile` (`mobile`),
  KEY `idx_school_campus` (`school_name`, `campus_name`),
  KEY `idx_tenant_last_login` (`tenant_id`, `last_login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园小程序用户';

CREATE TABLE `campus_user_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关注编号',
  `user_id` bigint NOT NULL COMMENT '关注人用户编号',
  `follow_user_id` bigint NOT NULL COMMENT '被关注用户编号',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_follow_user` (`user_id`, `follow_user_id`),
  KEY `idx_user_time` (`user_id`, `deleted`, `create_time`),
  KEY `idx_follow_user` (`follow_user_id`, `deleted`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园用户关注关系';

CREATE TABLE `campus_post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '帖子编号',
  `user_id` bigint NOT NULL COMMENT '发布用户编号',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  `school_name` varchar(100) NOT NULL DEFAULT '' COMMENT '学校名称快照',
  `campus_name` varchar(100) NOT NULL DEFAULT '' COMMENT '校区名称快照',
  `type` varchar(32) NOT NULL COMMENT '发布类型',
  `channel` varchar(32) NOT NULL COMMENT '内容频道',
  `title` varchar(80) NOT NULL COMMENT '标题',
  `content` varchar(2000) NOT NULL COMMENT '正文',
  `price` decimal(10,2) DEFAULT NULL COMMENT '价格',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `stock_total` int NOT NULL DEFAULT 1 COMMENT '初始库存数量',
  `stock_available` int NOT NULL DEFAULT 1 COMMENT '当前可售库存（已扣除待付款预占）',
  `sold_count` int NOT NULL DEFAULT 0 COMMENT '累计已售数量',
  `sale_status` tinyint NOT NULL DEFAULT 1 COMMENT '销售状态：1在售 2售罄',
  `location` varchar(160) NOT NULL DEFAULT '' COMMENT '公开展示位置；团购为大概位置',
  `merchant_address` varchar(255) NOT NULL DEFAULT '' COMMENT '商户实际地址，团购详情公开展示',
  `merchant_location_name` varchar(120) NOT NULL DEFAULT '' COMMENT '地图选择的门店或地点名称',
  `merchant_latitude` decimal(10,7) DEFAULT NULL COMMENT '门店纬度，仅用于地图导航',
  `merchant_longitude` decimal(10,7) DEFAULT NULL COMMENT '门店经度，仅用于地图导航',
  `trade_mode` varchar(64) NOT NULL DEFAULT '' COMMENT '交易或参与方式',
  `visible_range` varchar(32) NOT NULL DEFAULT '仅本校可见' COMMENT '可见范围',
  `contact` varchar(160) NOT NULL DEFAULT '' COMMENT '联系方式',
  `anonymous` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否匿名',
  `tags_json` text COMMENT '标签 JSON',
  `images_json` text COMMENT '图片 JSON',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0审核中 1已发布 2已下架',
  `audit_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '人工审核意见或驳回原因',
  `audit_time` datetime DEFAULT NULL COMMENT '人工审核时间',
  `auditor_id` bigint DEFAULT NULL COMMENT '后台审核人编号',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数',
  `collect_count` int NOT NULL DEFAULT 0 COMMENT '收藏数',
  `comment_count` int NOT NULL DEFAULT 0 COMMENT '评论数',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '浏览数',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`, `create_time`),
  KEY `idx_tenant_channel` (`tenant_id`, `channel`, `status`, `create_time`),
  KEY `idx_tenant_sale` (`tenant_id`, `type`, `status`, `sale_status`, `stock_available`),
  KEY `idx_job_audit` (`type`, `status`, `tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园社区帖子';

CREATE TABLE `campus_post_interaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '互动编号',
  `post_id` bigint NOT NULL COMMENT '帖子编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `type` varchar(16) NOT NULL COMMENT '类型：LIKE、COLLECT',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user_type` (`post_id`, `user_id`, `type`),
  KEY `idx_user_type_time` (`user_id`, `type`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园帖子点赞收藏关系';

CREATE TABLE `campus_post_comment` (
  `parent_id` bigint DEFAULT NULL,
  `like_count` int NOT NULL DEFAULT 0,
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论编号',
  `post_id` bigint NOT NULL COMMENT '帖子编号',
  `user_id` bigint NOT NULL COMMENT '评论用户编号',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  `content` varchar(300) NOT NULL COMMENT '评论内容',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0待审核，1已发布，2已隐藏',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_parent_status_time` (`parent_id`, `status`, `create_time`),
  KEY `idx_post_status_time` (`post_id`, `status`, `create_time`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园帖子评论';

CREATE TABLE `campus_post_comment_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园评论点赞';

CREATE TABLE `campus_post_comment_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `comment_id` bigint NOT NULL,
  `reporter_user_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `reason` varchar(32) NOT NULL,
  `detail` varchar(300) NOT NULL DEFAULT '',
  `status` tinyint NOT NULL DEFAULT 0,
  `result_note` varchar(300) NOT NULL DEFAULT '',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_reporter` (`comment_id`, `reporter_user_id`),
  KEY `idx_tenant_status_time` (`tenant_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园评论举报';

CREATE TABLE `campus_contact_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '联系申请编号',
  `post_id` bigint NOT NULL COMMENT '帖子编号',
  `requester_user_id` bigint NOT NULL COMMENT '申请用户编号',
  `target_user_id` bigint NOT NULL COMMENT '发布用户编号',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  `post_title` varchar(80) NOT NULL DEFAULT '' COMMENT '帖子标题快照',
  `requester_nickname` varchar(64) NOT NULL DEFAULT '' COMMENT '申请人昵称快照',
  `requester_mobile` varchar(20) NOT NULL DEFAULT '' COMMENT '申请人手机号快照',
  `target_nickname` varchar(64) NOT NULL DEFAULT '' COMMENT '发布者昵称快照',
  `target_mobile` varchar(20) NOT NULL DEFAULT '' COMMENT '发布者手机号快照',
  `message` varchar(300) NOT NULL DEFAULT '' COMMENT '联系申请说明',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1已处理 2已驳回',
  `result_note` varchar(300) NOT NULL DEFAULT '' COMMENT '处理说明',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_requester` (`post_id`, `requester_user_id`),
  KEY `idx_target_status_time` (`target_user_id`, `status`, `create_time`),
  KEY `idx_tenant_status_time` (`tenant_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园内容联系申请';

CREATE TABLE `campus_invite_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '邀请关系编号',
  `system_tenant_id` bigint NOT NULL COMMENT '发生邀请的校区租户',
  `inviter_user_id` bigint NOT NULL COMMENT '邀请人',
  `invitee_user_id` bigint NOT NULL COMMENT '被邀请人',
  `agent_id` bigint NOT NULL DEFAULT 0 COMMENT '关联代理编号',
  `invite_code` varchar(32) NOT NULL DEFAULT '' COMMENT '邀请码',
  `channel` varchar(50) NOT NULL DEFAULT '' COMMENT '渠道',
  `reward_status` tinyint NOT NULL DEFAULT 0 COMMENT '奖励状态：0无奖励 1待结算 2已结算 3无效',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，等同 system_tenant_id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invitee_tenant` (`invitee_user_id`, `system_tenant_id`),
  KEY `idx_inviter_tenant` (`inviter_user_id`, `system_tenant_id`),
  KEY `idx_agent` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园邀请裂变关系';

CREATE TABLE `campus_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品编号',
  `user_id` bigint NOT NULL COMMENT '发布者用户编号',
  `category_id` bigint NOT NULL DEFAULT 0 COMMENT '分类编号',
  `title` varchar(100) NOT NULL COMMENT '商品标题',
  `description` text COMMENT '商品描述',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `images` json DEFAULT NULL COMMENT '商品图片URL数组',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1在售 2已售出 3下架 4驳回',
  `audit_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '审核原因',
  `location` varchar(100) NOT NULL DEFAULT '' COMMENT '交易地点',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '浏览量',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞数',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，等同 system_tenant_id',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_status_created` (`tenant_id`, `status`, `create_time`),
  KEY `idx_user` (`user_id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_price` (`price`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园闲置商品';

CREATE TABLE `campus_trade_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单编号',
  `order_no` varchar(50) NOT NULL COMMENT '订单流水号',
  `buyer_id` bigint NOT NULL COMMENT '买家用户编号',
  `seller_id` bigint NOT NULL COMMENT '卖家用户编号',
  `product_id` bigint NOT NULL COMMENT '商品编号',
  `biz_type` tinyint NOT NULL DEFAULT 1 COMMENT '业务类型：1二手交易 4代拿代办',
  `seller_phone_snapshot` varchar(20) NOT NULL DEFAULT '' COMMENT '卖家联系电话快照',
  `agent_user_id` bigint NOT NULL DEFAULT 0 COMMENT '下单时校区代理用户快照',
  `inviter_user_id` bigint NOT NULL DEFAULT 0 COMMENT '下单时邀请人快照',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `commission_rule_snapshot` json DEFAULT NULL COMMENT '分润规则快照',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待付款 1已付款 2已完成 3已取消 4已退款',
  `inventory_state` tinyint NOT NULL DEFAULT 0 COMMENT '库存状态：0旧订单未处理 1已预占 2已售出 3已释放或退回',
  `fulfillment_status` tinyint NOT NULL DEFAULT 0 COMMENT '履约状态：0待付款 1待接单 2进行中 3待确认 4已完成 5已取消',
  `accept_expires_at` datetime DEFAULT NULL COMMENT '代拿代办接单截止时间',
  `accepted_at` datetime DEFAULT NULL COMMENT '接单时间',
  `submitted_at` datetime DEFAULT NULL COMMENT '接单人提交完成时间',
  `completion_note` varchar(500) NOT NULL DEFAULT '' COMMENT '接单人完成说明',
  `completion_images_json` varchar(3000) NOT NULL DEFAULT '[]' COMMENT '接单人完成凭证图片 JSON',
  `confirm_expires_at` datetime DEFAULT NULL COMMENT '发布人确认或申诉截止时间',
  `confirm_reminder_stage` tinyint NOT NULL DEFAULT 0 COMMENT '确认提醒阶段：0未提醒 1剩余12小时 2剩余2小时',
  `dispute_status` tinyint NOT NULL DEFAULT 0 COMMENT '申诉状态：0无 1待处理 2接单人胜诉 3发布人胜诉',
  `dispute_reason` varchar(500) NOT NULL DEFAULT '' COMMENT '发布人申诉原因',
  `dispute_images_json` varchar(3000) NOT NULL DEFAULT '[]' COMMENT '发布人申诉凭证图片 JSON',
  `disputed_at` datetime DEFAULT NULL COMMENT '申诉时间',
  `dispute_resolution` varchar(500) NOT NULL DEFAULT '' COMMENT '平台裁决说明',
  `dispute_resolved_at` datetime DEFAULT NULL COMMENT '申诉裁决时间',
  `dispute_resolver_id` bigint DEFAULT NULL COMMENT '后台裁决人编号',
  `auto_confirmed` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否由系统超时自动确认',
  `paid_at` datetime DEFAULT NULL COMMENT '支付时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，等同 system_tenant_id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_tenant_status_created` (`tenant_id`, `status`, `create_time`),
  KEY `idx_buyer` (`buyer_id`),
  KEY `idx_seller` (`seller_id`),
  KEY `idx_product` (`product_id`),
  KEY `idx_errand_accept` (`biz_type`, `fulfillment_status`, `accept_expires_at`),
  KEY `idx_errand_confirm` (`biz_type`, `fulfillment_status`, `dispute_status`, `confirm_expires_at`),
  KEY `idx_agent_user` (`agent_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园交易订单';

CREATE TABLE `campus_trade_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '交易消息编号',
  `order_id` bigint NOT NULL COMMENT '订单编号',
  `sender_id` bigint NOT NULL COMMENT '发送用户编号',
  `receiver_id` bigint NOT NULL COMMENT '接收用户编号',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  `content` varchar(500) NOT NULL COMMENT '消息内容',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_order_time` (`order_id`, `create_time`),
  KEY `idx_receiver_time` (`receiver_id`, `create_time`),
  KEY `idx_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园订单交易会话消息';

CREATE TABLE `campus_commission_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分润规则编号',
  `biz_type` tinyint NOT NULL COMMENT '业务类型：1闲置交易 2商城 3兼职 4代拿 5广告',
  `platform_rate` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '平台抽佣比例',
  `agent_rate` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '校区代理分润比例',
  `inviter_rate` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '邀请人分润比例',
  `blogger_rate` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '博主分润比例',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0开启 1关闭',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，0表示平台默认规则',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_biz` (`tenant_id`, `biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园分润规则';

CREATE TABLE `campus_commission_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分润记录编号',
  `order_id` bigint NOT NULL COMMENT '订单编号',
  `order_no` varchar(50) NOT NULL COMMENT '订单流水号',
  `biz_type` tinyint NOT NULL COMMENT '业务类型',
  `receiver_user_id` bigint NOT NULL COMMENT '收益接收用户编号',
  `receiver_type` tinyint NOT NULL COMMENT '接收方类型：1平台 2校区代理 3邀请人 4博主 5商家 6代办接单人',
  `base_amount` decimal(10,2) NOT NULL COMMENT '参与计算金额',
  `rate` decimal(5,2) NOT NULL COMMENT '分润比例',
  `amount` decimal(10,2) NOT NULL COMMENT '分润金额',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待结算 1可提现 2已提现 3已取消',
  `settled_at` datetime DEFAULT NULL COMMENT '结算时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号，等同 system_tenant_id',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_created` (`tenant_id`, `create_time`),
  KEY `idx_order` (`order_id`),
  KEY `idx_receiver_status` (`receiver_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园分润记录';

INSERT INTO `campus_commission_rule`
(`biz_type`, `platform_rate`, `agent_rate`, `inviter_rate`, `blogger_rate`, `status`, `tenant_id`)
VALUES
(1, 5.00, 20.00, 5.00, 0.00, 0, 0);
