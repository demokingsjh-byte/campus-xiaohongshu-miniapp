-- 云点校园内容安全审核记录（可重复执行）

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `campus_content_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '审核记录编号',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  `entity_type` varchar(16) NOT NULL COMMENT '对象类型：POST/COMMENT',
  `entity_id` bigint NOT NULL COMMENT '对象编号',
  `user_id` bigint NOT NULL COMMENT '发布用户编号',
  `content_type` varchar(16) NOT NULL COMMENT '内容类型：TEXT/IMAGE',
  `content_ref` varchar(1024) NOT NULL DEFAULT '' COMMENT '文字哈希或图片地址',
  `trace_id` varchar(128) DEFAULT NULL COMMENT '微信审核任务 TraceId',
  `suggest` varchar(16) NOT NULL COMMENT '审核建议：PASS/PENDING/REVIEW/RISKY/ERROR',
  `label` varchar(64) NOT NULL DEFAULT '' COMMENT '风险标签',
  `raw_result` mediumtext COMMENT '微信原始结果',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trace_id` (`trace_id`),
  KEY `idx_entity` (`entity_type`, `entity_id`),
  KEY `idx_tenant_suggest_time` (`tenant_id`, `suggest`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园内容安全审核记录';
