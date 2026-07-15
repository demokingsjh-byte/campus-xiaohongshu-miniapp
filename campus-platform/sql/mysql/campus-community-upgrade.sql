-- 云点校园社区内容与互动链路（可重复执行）

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `campus_post` (
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
  `location` varchar(160) NOT NULL DEFAULT '' COMMENT '发布位置',
  `trade_mode` varchar(64) NOT NULL DEFAULT '' COMMENT '交易或参与方式',
  `visible_range` varchar(32) NOT NULL DEFAULT '仅本校可见' COMMENT '可见范围',
  `contact` varchar(160) NOT NULL DEFAULT '' COMMENT '联系方式',
  `anonymous` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否匿名',
  `tags_json` text COMMENT '标签 JSON',
  `images_json` text COMMENT '图片 JSON',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0审核中 1已发布 2已下架',
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
  KEY `idx_tenant_channel` (`tenant_id`, `channel`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园社区帖子';

CREATE TABLE IF NOT EXISTS `campus_post_interaction` (
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

-- 小程序埋点明细：保留可追溯的页面与行为事件，报表按服务端时间统计。
CREATE TABLE IF NOT EXISTS `campus_analytics_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件编号',
  `user_id` bigint NOT NULL COMMENT '校园用户编号',
  `session_id` varchar(64) NOT NULL COMMENT '客户端会话编号',
  `event_name` varchar(64) NOT NULL COMMENT '事件名称',
  `page_path` varchar(255) NOT NULL DEFAULT '' COMMENT '页面路径',
  `properties_json` text COMMENT '事件属性 JSON',
  `client_time` datetime DEFAULT NULL COMMENT '客户端发生时间',
  `client_version` varchar(32) NOT NULL DEFAULT '' COMMENT '小程序版本',
  `source_scene` varchar(64) NOT NULL DEFAULT '' COMMENT '微信入口场景值',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '服务端接收时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_event_time` (`tenant_id`, `event_name`, `create_time`),
  KEY `idx_tenant_user_time` (`tenant_id`, `user_id`, `create_time`),
  KEY `idx_session_time` (`session_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园小程序埋点事件';

-- 小程序在线会话：前台每 60 秒心跳，5 分钟未活跃自动视为离线。
CREATE TABLE IF NOT EXISTS `campus_analytics_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话编号',
  `user_id` bigint NOT NULL COMMENT '校园用户编号',
  `session_id` varchar(64) NOT NULL COMMENT '客户端会话编号',
  `entry_page` varchar(255) NOT NULL DEFAULT '' COMMENT '进入页面',
  `last_page` varchar(255) NOT NULL DEFAULT '' COMMENT '最后页面',
  `source_scene` varchar(64) NOT NULL DEFAULT '' COMMENT '微信入口场景值',
  `client_version` varchar(32) NOT NULL DEFAULT '' COMMENT '小程序版本',
  `online_status` bit(1) NOT NULL DEFAULT b'1' COMMENT '前台在线状态',
  `first_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次活跃时间',
  `last_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  `ended_at` datetime DEFAULT NULL COMMENT '主动离开时间',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_session` (`tenant_id`, `session_id`),
  KEY `idx_tenant_online_seen` (`tenant_id`, `online_status`, `last_seen_at`),
  KEY `idx_tenant_user_seen` (`tenant_id`, `user_id`, `last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园小程序在线会话';

-- 每日汇总表为后续定时归档和长期趋势预留；当前看板实时读取业务表与事件表。
CREATE TABLE IF NOT EXISTS `campus_analytics_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '汇总编号',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `registered_users` int NOT NULL DEFAULT 0 COMMENT '新增注册用户数',
  `active_users` int NOT NULL DEFAULT 0 COMMENT '活跃用户数',
  `post_count` int NOT NULL DEFAULT 0 COMMENT '发布数',
  `order_count` int NOT NULL DEFAULT 0 COMMENT '订单数',
  `paid_order_count` int NOT NULL DEFAULT 0 COMMENT '支付订单数',
  `revenue_amount` decimal(14,2) NOT NULL DEFAULT 0.00 COMMENT '已支付未退款订单金额',
  `event_count` bigint NOT NULL DEFAULT 0 COMMENT '事件数',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_date` (`tenant_id`, `stat_date`),
  KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园小程序每日数据汇总';
