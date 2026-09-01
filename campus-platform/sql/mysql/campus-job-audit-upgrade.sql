-- 兼职信息人工审核字段、索引及后台菜单；可重复执行。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `upgrade_campus_job_audit`;
DELIMITER $$
CREATE PROCEDURE `upgrade_campus_job_audit`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post'
      AND COLUMN_NAME = 'audit_reason'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `audit_reason` varchar(200) NOT NULL DEFAULT ''
      COMMENT '人工审核意见或驳回原因' AFTER `status`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post'
      AND COLUMN_NAME = 'audit_time'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `audit_time` datetime DEFAULT NULL COMMENT '人工审核时间' AFTER `audit_reason`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post'
      AND COLUMN_NAME = 'auditor_id'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `auditor_id` bigint DEFAULT NULL COMMENT '后台审核人编号' AFTER `audit_time`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post'
      AND INDEX_NAME = 'idx_job_audit'
  ) THEN
    ALTER TABLE `campus_post`
      ADD KEY `idx_job_audit` (`type`, `status`, `tenant_id`, `create_time`);
  END IF;
END$$
DELIMITER ;
CALL `upgrade_campus_job_audit`();
DROP PROCEDURE IF EXISTS `upgrade_campus_job_audit`;

-- 旧版本中尚未经过人工审核的已发布兼职，升级后重新进入待审核队列。
-- audit_time 可保证脚本重跑时不会影响已经人工通过的记录。
UPDATE `campus_post`
SET `status` = 0, `audit_reason` = '', `auditor_id` = NULL,
    `updater` = 'job-audit-upgrade', `update_time` = NOW()
WHERE `type` = 'job' AND `status` = 1 AND `audit_time` IS NULL AND `deleted` = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900930, '兼职审核', 'campus:job-audit:query', 2, 9, 900000, 'job-audit',
     'ep:checked', 'campus/job-audit/index', 'CampusJobAudit', 0, b'1', b'1', b'1',
     'campus', NOW(), 'campus', NOW(), b'0'),
    (900931, '兼职审核操作', 'campus:job-audit:review', 3, 1, 900930, '',
     '', '', NULL, 0, b'1', b'1', b'1',
     'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), permission = VALUES(permission), type = VALUES(type), sort = VALUES(sort),
    parent_id = VALUES(parent_id), path = VALUES(path), icon = VALUES(icon),
    component = VALUES(component), component_name = VALUES(component_name),
    status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';

-- 已拥有“内容管理”的角色自动获得兼职审核菜单及操作权限。
INSERT INTO system_role_menu
    (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT source_role.role_id, target_menu.menu_id, 'campus', NOW(), 'campus', NOW(), b'0', source_role.tenant_id
FROM system_role_menu source_role
JOIN (SELECT 900930 AS menu_id UNION ALL SELECT 900931) target_menu ON 1 = 1
WHERE source_role.menu_id = 900800 AND source_role.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu existing
    WHERE existing.role_id = source_role.role_id
      AND existing.menu_id = target_menu.menu_id
      AND existing.tenant_id = source_role.tenant_id
      AND existing.deleted = b'0'
  )
ON DUPLICATE KEY UPDATE
    updater = 'campus', update_time = NOW(), deleted = b'0';
