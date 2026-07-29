-- 校园二手订单退款字段与后台订单中心菜单；可重复执行。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `upgrade_campus_trade_refund`;
DELIMITER $$
CREATE PROCEDURE `upgrade_campus_trade_refund`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'refund_no'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `refund_no` varchar(64) DEFAULT NULL COMMENT '商户退款单号' AFTER `wx_transaction_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'wx_refund_id'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `wx_refund_id` varchar(64) DEFAULT NULL COMMENT '微信退款单号' AFTER `refund_no`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'refund_status'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `refund_status` tinyint NOT NULL DEFAULT 0 COMMENT '退款状态：0未退款 1处理中 2成功 3失败' AFTER `wx_refund_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'refund_amount'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `refund_amount` decimal(10,2) DEFAULT NULL COMMENT '退款金额' AFTER `refund_status`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'refund_reason'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `refund_reason` varchar(128) NOT NULL DEFAULT '' COMMENT '退款原因' AFTER `refund_amount`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'refund_requested_at'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `refund_requested_at` datetime DEFAULT NULL COMMENT '发起退款时间' AFTER `refund_reason`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'refunded_at'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `refunded_at` datetime DEFAULT NULL COMMENT '退款成功时间' AFTER `refund_requested_at`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'refund_error'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `refund_error` varchar(255) DEFAULT NULL COMMENT '退款失败或查询错误' AFTER `refunded_at`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'refund_operator'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `refund_operator` varchar(64) NOT NULL DEFAULT '' COMMENT '退款操作人' AFTER `refund_error`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'refund_notify_at'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `refund_notify_at` datetime DEFAULT NULL COMMENT '退款回调时间' AFTER `refund_operator`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND INDEX_NAME = 'uk_refund_no'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD UNIQUE KEY `uk_refund_no` (`refund_no`);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND INDEX_NAME = 'idx_refund_status_updated'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD KEY `idx_refund_status_updated` (`refund_status`, `update_time`);
  END IF;
END$$
DELIMITER ;
CALL `upgrade_campus_trade_refund`();
DROP PROCEDURE IF EXISTS `upgrade_campus_trade_refund`;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900920, '订单中心', 'campus:trade-order:query', 2, 9, 900000, 'trade-order',
     'ep:tickets', 'campus/order/index', 'CampusTradeOrder', 0, b'1', b'1', b'1',
     'campus', NOW(), 'campus', NOW(), b'0'),
    (900921, '订单退款', 'campus:trade-order:refund', 3, 1, 900920, '',
     '', '', NULL, 0, b'1', b'1', b'1',
     'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    permission = VALUES(permission),
    type = VALUES(type),
    sort = VALUES(sort),
    parent_id = VALUES(parent_id),
    path = VALUES(path),
    icon = VALUES(icon),
    component = VALUES(component),
    component_name = VALUES(component_name),
    status = 0,
    visible = b'1',
    updater = 'campus',
    update_time = NOW(),
    deleted = b'0';

-- Super administrator menu permissions. Keep this idempotent so the upgrade can
-- be rerun after restoring the database or refreshing the menu cache.
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 1, menu_id, 'campus', NOW(), 'campus', NOW(), b'0', 0
FROM (
    SELECT 900920 AS menu_id
    UNION ALL SELECT 900921
) AS campus_order_menus
WHERE NOT EXISTS (
    SELECT 1 FROM system_role_menu rm
    WHERE rm.role_id = 1 AND rm.menu_id = campus_order_menus.menu_id AND rm.deleted = b'0'
);
