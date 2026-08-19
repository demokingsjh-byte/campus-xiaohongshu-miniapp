-- 校园用户关注关系（可重复执行）

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `campus_user_follow` (
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
