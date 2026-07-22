-- 云点校园内容治理：用户举报记录（可重复执行）

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `campus_post_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '举报编号',
  `post_id` bigint NOT NULL COMMENT '帖子编号',
  `reporter_user_id` bigint NOT NULL COMMENT '举报用户编号',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  `reason` varchar(32) NOT NULL COMMENT '举报原因',
  `detail` varchar(300) NOT NULL DEFAULT '' COMMENT '补充说明',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0待处理 1已处理 2已驳回',
  `result_note` varchar(300) NOT NULL DEFAULT '' COMMENT '处理说明',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_reporter` (`post_id`, `reporter_user_id`),
  KEY `idx_tenant_status_time` (`tenant_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园帖子举报记录';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900900, '举报处理', 'campus:post-report:query', 2, 9, 900000, 'post-report', 'ep:warning', 'campus/base/index', 'CampusPostReport', 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), sort = VALUES(sort), parent_id = VALUES(parent_id), path = VALUES(path), icon = VALUES(icon), component = VALUES(component), component_name = VALUES(component_name), status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900901, '举报处理修改', 'campus:post-report:update', 3, 1, 900900, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900902, '举报记录删除', 'campus:post-report:delete', 3, 2, 900900, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), sort = VALUES(sort), status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900903, '评论管理', 'campus:comment:query', 2, 10, 900000, 'comment', 'ep:chat-dot-round', 'campus/base/index', 'CampusComment', 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900904, '评论审核修改', 'campus:comment:update', 3, 1, 900903, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900905, '评论删除', 'campus:comment:delete', 3, 2, 900903, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), sort = VALUES(sort), status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900906, '评论举报处理', 'campus:comment-report:query', 2, 11, 900000, 'comment-report', 'ep:warning', 'campus/base/index', 'CampusCommentReport', 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900907, '评论举报修改', 'campus:comment-report:update', 3, 1, 900906, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900908, '评论举报删除', 'campus:comment-report:delete', 3, 2, 900906, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), sort = VALUES(sort), status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';
