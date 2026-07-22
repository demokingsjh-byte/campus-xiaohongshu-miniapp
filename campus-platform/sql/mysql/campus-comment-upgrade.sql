-- 校园社区评论功能（可重复执行）

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `campus_post_comment` (
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
  KEY `idx_post_status_time` (`post_id`, `status`, `create_time`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园帖子评论';

UPDATE `campus_post` p
SET p.`comment_count` = (
  SELECT COUNT(*) FROM `campus_post_comment` c
  WHERE c.`post_id` = p.`id` AND c.`status` = 1 AND c.`deleted` = b'0'
);

-- P1 评论互动与治理字段/表（MySQL 8，可重复执行）
SET @campus_comment_parent_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'campus_post_comment' AND column_name = 'parent_id'
);
SET @campus_comment_parent_sql = IF(
  @campus_comment_parent_exists = 0,
  'ALTER TABLE `campus_post_comment` ADD COLUMN `parent_id` bigint DEFAULT NULL AFTER `user_id`',
  'SELECT 1'
);
PREPARE campus_comment_parent_stmt FROM @campus_comment_parent_sql;
EXECUTE campus_comment_parent_stmt;
DEALLOCATE PREPARE campus_comment_parent_stmt;

SET @campus_comment_like_count_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'campus_post_comment' AND column_name = 'like_count'
);
SET @campus_comment_like_count_sql = IF(
  @campus_comment_like_count_exists = 0,
  'ALTER TABLE `campus_post_comment` ADD COLUMN `like_count` int NOT NULL DEFAULT 0 AFTER `status`',
  'SELECT 1'
);
PREPARE campus_comment_like_count_stmt FROM @campus_comment_like_count_sql;
EXECUTE campus_comment_like_count_stmt;
DEALLOCATE PREPARE campus_comment_like_count_stmt;

CREATE TABLE IF NOT EXISTS `campus_post_comment_like` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `campus_post_comment_report` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE `campus_post_comment` c
SET c.`like_count` = (
  SELECT COUNT(*) FROM `campus_post_comment_like` l
  WHERE l.`comment_id` = c.`id` AND l.`deleted` = b'0'
);
