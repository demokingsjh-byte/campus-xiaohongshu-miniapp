-- 代拿代办完成确认、超时自动结算及申诉仲裁升级；可重复执行。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `upgrade_campus_errand_confirmation`;
DELIMITER $$
CREATE PROCEDURE `upgrade_campus_errand_confirmation`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'completion_note') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `completion_note` varchar(500) NOT NULL DEFAULT ''
      COMMENT '接单人完成说明' AFTER `submitted_at`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'completion_images_json') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `completion_images_json` varchar(3000) NOT NULL DEFAULT '[]'
      COMMENT '接单人完成凭证图片 JSON' AFTER `completion_note`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'confirm_expires_at') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `confirm_expires_at` datetime DEFAULT NULL
      COMMENT '发布人确认或申诉截止时间' AFTER `completion_images_json`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'confirm_reminder_stage') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `confirm_reminder_stage` tinyint NOT NULL DEFAULT 0
      COMMENT '确认提醒阶段：0未提醒 1剩余12小时 2剩余2小时' AFTER `confirm_expires_at`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'dispute_status') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `dispute_status` tinyint NOT NULL DEFAULT 0
      COMMENT '申诉状态：0无 1待处理 2接单人胜诉 3发布人胜诉' AFTER `confirm_reminder_stage`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'dispute_reason') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `dispute_reason` varchar(500) NOT NULL DEFAULT ''
      COMMENT '发布人申诉原因' AFTER `dispute_status`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'dispute_images_json') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `dispute_images_json` varchar(3000) NOT NULL DEFAULT '[]'
      COMMENT '发布人申诉凭证图片 JSON' AFTER `dispute_reason`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'disputed_at') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `disputed_at` datetime DEFAULT NULL
      COMMENT '申诉时间' AFTER `dispute_images_json`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'dispute_resolution') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `dispute_resolution` varchar(500) NOT NULL DEFAULT ''
      COMMENT '平台裁决说明' AFTER `disputed_at`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'dispute_resolved_at') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `dispute_resolved_at` datetime DEFAULT NULL
      COMMENT '申诉裁决时间' AFTER `dispute_resolution`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'dispute_resolver_id') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `dispute_resolver_id` bigint DEFAULT NULL
      COMMENT '后台裁决人编号' AFTER `dispute_resolved_at`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'auto_confirmed') THEN
    ALTER TABLE `campus_trade_order` ADD COLUMN `auto_confirmed` bit(1) NOT NULL DEFAULT b'0'
      COMMENT '是否由系统超时自动确认' AFTER `dispute_resolver_id`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'campus_trade_order' AND INDEX_NAME = 'idx_errand_confirm') THEN
    ALTER TABLE `campus_trade_order` ADD KEY `idx_errand_confirm`
      (`biz_type`, `fulfillment_status`, `dispute_status`, `confirm_expires_at`);
  END IF;
END$$
DELIMITER ;
CALL `upgrade_campus_errand_confirmation`();
DROP PROCEDURE IF EXISTS `upgrade_campus_errand_confirmation`;

-- 升级前已经由接单人提交的任务，从原提交时间起补足 24 小时确认期。
UPDATE `campus_trade_order`
SET `confirm_expires_at` = DATE_ADD(COALESCE(`submitted_at`, `update_time`, NOW()), INTERVAL 24 HOUR),
    `updater` = 'errand-confirm-upgrade', `update_time` = NOW()
WHERE `biz_type` = 4 AND `status` = 1 AND `fulfillment_status` = 3
  AND `confirm_expires_at` IS NULL AND `deleted` = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900940, '代办申诉', 'campus:errand-dispute:query', 2, 10, 900000, 'errand-dispute',
     'ep:warning', 'campus/errand-dispute/index', 'CampusErrandDispute', 0, b'1', b'1', b'1',
     'campus', NOW(), 'campus', NOW(), b'0'),
    (900941, '代办申诉裁决', 'campus:errand-dispute:resolve', 3, 1, 900940, '',
     '', '', NULL, 0, b'1', b'1', b'1',
     'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), permission = VALUES(permission), type = VALUES(type), sort = VALUES(sort),
    parent_id = VALUES(parent_id), path = VALUES(path), icon = VALUES(icon),
    component = VALUES(component), component_name = VALUES(component_name), status = 0,
    visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';

-- 已拥有“内容管理”的角色自动获得申诉查看和裁决权限。
INSERT INTO system_role_menu
    (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT source_role.role_id, target_menu.menu_id, 'campus', NOW(), 'campus', NOW(), b'0', source_role.tenant_id
FROM system_role_menu source_role
JOIN (SELECT 900940 AS menu_id UNION ALL SELECT 900941) target_menu ON 1 = 1
WHERE source_role.menu_id = 900800 AND source_role.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu existing
    WHERE existing.role_id = source_role.role_id AND existing.menu_id = target_menu.menu_id
      AND existing.tenant_id = source_role.tenant_id AND existing.deleted = b'0'
  )
ON DUPLICATE KEY UPDATE updater = 'campus', update_time = NOW(), deleted = b'0';
