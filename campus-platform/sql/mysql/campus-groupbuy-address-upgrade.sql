-- 商家团购实际地址与公开大概位置升级；可重复执行。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `upgrade_campus_groupbuy_address`;
DELIMITER $$
CREATE PROCEDURE `upgrade_campus_groupbuy_address`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post'
      AND COLUMN_NAME = 'merchant_address'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `merchant_address` varchar(255) NOT NULL DEFAULT ''
      COMMENT '商户实际地址，团购详情公开展示' AFTER `location`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post'
      AND COLUMN_NAME = 'merchant_location_name'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `merchant_location_name` varchar(120) NOT NULL DEFAULT ''
      COMMENT '地图选择的门店或地点名称' AFTER `merchant_address`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post'
      AND COLUMN_NAME = 'merchant_latitude'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `merchant_latitude` decimal(10,7) DEFAULT NULL
      COMMENT '门店纬度，仅用于地图导航' AFTER `merchant_location_name`;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post'
      AND COLUMN_NAME = 'merchant_longitude'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `merchant_longitude` decimal(10,7) DEFAULT NULL
      COMMENT '门店经度，仅用于地图导航' AFTER `merchant_latitude`;
  END IF;
END$$
DELIMITER ;
CALL `upgrade_campus_groupbuy_address`();
DROP PROCEDURE IF EXISTS `upgrade_campus_groupbuy_address`;
