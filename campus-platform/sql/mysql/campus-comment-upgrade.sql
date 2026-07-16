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
