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
