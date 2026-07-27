-- Campus second-hand order foundation migration. Safe to run repeatedly.
DROP PROCEDURE IF EXISTS `upgrade_campus_trade_order`;
DELIMITER $$
CREATE PROCEDURE `upgrade_campus_trade_order`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'expires_at'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `expires_at` datetime DEFAULT NULL COMMENT '待付款过期时间' AFTER `status`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'closed_at'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `closed_at` datetime DEFAULT NULL COMMENT '订单关闭时间' AFTER `completed_at`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'close_reason'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `close_reason` varchar(32) NOT NULL DEFAULT '' COMMENT '关闭原因' AFTER `closed_at`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'item_title_snapshot'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `item_title_snapshot` varchar(255) NOT NULL DEFAULT '' COMMENT '商品标题快照' AFTER `amount`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'item_cover_snapshot'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `item_cover_snapshot` varchar(512) NOT NULL DEFAULT '' COMMENT '商品封面快照' AFTER `item_title_snapshot`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'version'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER `tenant_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND INDEX_NAME = 'idx_product_status_expires'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD KEY `idx_product_status_expires` (`product_id`, `status`, `expires_at`);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND INDEX_NAME = 'idx_buyer_status_created'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD KEY `idx_buyer_status_created` (`buyer_id`, `status`, `create_time`);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND INDEX_NAME = 'idx_seller_status_created'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD KEY `idx_seller_status_created` (`seller_id`, `status`, `create_time`);
  END IF;
END$$
DELIMITER ;
CALL `upgrade_campus_trade_order`();
DROP PROCEDURE IF EXISTS `upgrade_campus_trade_order`;

-- Give legacy unpaid orders the same timeout rule after the new columns are installed.
UPDATE `campus_trade_order`
SET `expires_at` = DATE_ADD(`create_time`, INTERVAL 15 MINUTE)
WHERE `status` = 0 AND `expires_at` IS NULL AND `deleted` = b'0';
