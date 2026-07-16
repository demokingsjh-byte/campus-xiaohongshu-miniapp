-- 云点校园联系发布者申请（可重复执行）

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `campus_contact_request` (
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

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900910, '联系申请', 'campus:contact-request:query', 2, 10, 900000, 'contact-request', 'ep:chat-dot-round', 'campus/base/index', 'CampusContactRequest', 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), sort = VALUES(sort), parent_id = VALUES(parent_id), path = VALUES(path), icon = VALUES(icon), component = VALUES(component), component_name = VALUES(component_name), status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900911, '联系申请处理', 'campus:contact-request:update', 3, 1, 900910, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900912, '联系申请删除', 'campus:contact-request:delete', 3, 2, 900910, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), sort = VALUES(sort), status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';
