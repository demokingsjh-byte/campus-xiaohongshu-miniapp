-- 校园代拿代办订单履约升级；可重复执行。
-- 业务约定：buyer_id=任务发布/付款人，seller_id=接单人，biz_type=4。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `upgrade_campus_errand_order`;
DELIMITER $$
CREATE PROCEDURE `upgrade_campus_errand_order`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'biz_type'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `biz_type` tinyint NOT NULL DEFAULT 1
      COMMENT '业务类型：1二手交易 4代拿代办' AFTER `product_id`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'fulfillment_status'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `fulfillment_status` tinyint NOT NULL DEFAULT 0
      COMMENT '履约状态：0待付款 1待接单 2进行中 3待确认 4已完成 5已取消' AFTER `status`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'accept_expires_at'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `accept_expires_at` datetime DEFAULT NULL COMMENT '代拿代办接单截止时间'
      AFTER `fulfillment_status`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'accepted_at'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `accepted_at` datetime DEFAULT NULL COMMENT '接单时间' AFTER `accept_expires_at`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'submitted_at'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `submitted_at` datetime DEFAULT NULL COMMENT '接单人提交完成时间' AFTER `accepted_at`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND INDEX_NAME = 'idx_errand_accept'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD KEY `idx_errand_accept` (`biz_type`, `fulfillment_status`, `accept_expires_at`);
  END IF;
END$$
DELIMITER ;
CALL `upgrade_campus_errand_order`();
DROP PROCEDURE IF EXISTS `upgrade_campus_errand_order`;

-- 旧订单全部按二手交易处理；seller_id=0 仅用于尚未被接单的代办。
UPDATE `campus_trade_order`
SET `biz_type` = 1
WHERE `biz_type` IS NULL OR `biz_type` = 0;

ALTER TABLE `campus_commission_record`
  MODIFY COLUMN `receiver_type` tinyint NOT NULL
  COMMENT '接收方类型：1平台 2校区代理 3邀请人 4博主 5商家 6代办接单人';
