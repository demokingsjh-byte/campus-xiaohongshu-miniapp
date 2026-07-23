-- 云点校园通知中心（可重复执行）

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `campus_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '通知编号',
  `user_id` bigint NOT NULL COMMENT '接收用户编号',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  `actor_user_id` bigint DEFAULT NULL COMMENT '触发用户编号',
  `actor_nickname` varchar(64) NOT NULL DEFAULT '' COMMENT '触发用户昵称快照',
  `type` varchar(16) NOT NULL COMMENT '通知分类：INTERACTION、SYSTEM',
  `event_type` varchar(32) NOT NULL COMMENT '事件类型：LIKE、COMMENT、REPLY、SYSTEM_NOTICE',
  `title` varchar(200) NOT NULL COMMENT '通知标题',
  `content` varchar(500) NOT NULL DEFAULT '' COMMENT '通知摘要',
  `target_type` varchar(16) NOT NULL DEFAULT 'SYSTEM' COMMENT '跳转目标：POST、PRODUCT、SYSTEM',
  `target_id` bigint DEFAULT NULL COMMENT '跳转目标编号',
  `read_time` datetime DEFAULT NULL COMMENT '已读时间',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_read_time` (`user_id`, `read_time`, `create_time`),
  KEY `idx_tenant_time` (`tenant_id`, `create_time`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园用户通知';
