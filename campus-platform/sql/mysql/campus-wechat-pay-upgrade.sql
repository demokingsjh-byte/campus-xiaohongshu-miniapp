-- 校园二手微信支付字段；可重复执行。
DROP PROCEDURE IF EXISTS `upgrade_campus_wechat_pay`;
DELIMITER $$
CREATE PROCEDURE `upgrade_campus_wechat_pay`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'wx_transaction_id'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `wx_transaction_id` varchar(64) DEFAULT NULL COMMENT '微信支付交易号' AFTER `paid_at`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND INDEX_NAME = 'uk_wx_transaction_id'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD UNIQUE KEY `uk_wx_transaction_id` (`wx_transaction_id`);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND INDEX_NAME = 'idx_buyer_product_status'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD KEY `idx_buyer_product_status` (`buyer_id`, `product_id`, `status`);
  END IF;
END$$
DELIMITER ;
CALL `upgrade_campus_wechat_pay`();
DROP PROCEDURE IF EXISTS `upgrade_campus_wechat_pay`;
