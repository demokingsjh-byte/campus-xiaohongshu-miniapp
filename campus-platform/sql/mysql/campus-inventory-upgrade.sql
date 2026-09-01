-- 校园二手商品库存与自动售罄升级；可重复执行。
DROP PROCEDURE IF EXISTS `upgrade_campus_inventory`;
DELIMITER $$
CREATE PROCEDURE `upgrade_campus_inventory`()
BEGIN
  DECLARE inventory_added BOOLEAN DEFAULT FALSE;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post' AND COLUMN_NAME = 'stock_total'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `stock_total` int NOT NULL DEFAULT 1 COMMENT '初始库存数量' AFTER `original_price`;
    SET inventory_added = TRUE;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post' AND COLUMN_NAME = 'stock_available'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `stock_available` int NOT NULL DEFAULT 1 COMMENT '当前可售库存（已扣除待付款预占）' AFTER `stock_total`;
    SET inventory_added = TRUE;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post' AND COLUMN_NAME = 'sold_count'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `sold_count` int NOT NULL DEFAULT 0 COMMENT '累计已售数量' AFTER `stock_available`;
    SET inventory_added = TRUE;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post' AND COLUMN_NAME = 'sale_status'
  ) THEN
    ALTER TABLE `campus_post`
      ADD COLUMN `sale_status` tinyint NOT NULL DEFAULT 1 COMMENT '销售状态：1在售 2售罄' AFTER `sold_count`;
    SET inventory_added = TRUE;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order' AND COLUMN_NAME = 'inventory_state'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `inventory_state` tinyint NOT NULL DEFAULT 0
      COMMENT '库存状态：0旧订单未处理 1已预占 2已售出 3已释放或退回' AFTER `status`;
    SET inventory_added = TRUE;
  END IF;

  IF inventory_added THEN
    UPDATE `campus_trade_order`
    SET `inventory_state` = CASE
      WHEN `status` IN (1, 2) THEN 2
      WHEN `status` = 0 AND (`expires_at` IS NULL OR `expires_at` > NOW()) THEN 1
      ELSE 3
    END
    WHERE `deleted` = b'0' AND `biz_type` = 1 AND `inventory_state` = 0;

    UPDATE `campus_post` p
    LEFT JOIN (
      SELECT `product_id`,
             SUM(CASE WHEN `inventory_state` = 2 THEN 1 ELSE 0 END) AS sold_qty,
             SUM(CASE WHEN `inventory_state` = 1 THEN 1 ELSE 0 END) AS reserved_qty
      FROM `campus_trade_order`
      WHERE `deleted` = b'0' AND `biz_type` = 1
      GROUP BY `product_id`
    ) o ON o.product_id = p.id
    SET p.stock_total = GREATEST(p.stock_total, COALESCE(o.sold_qty, 0) + COALESCE(o.reserved_qty, 0), 1),
        p.stock_available = GREATEST(
          GREATEST(p.stock_total, COALESCE(o.sold_qty, 0) + COALESCE(o.reserved_qty, 0), 1)
            - COALESCE(o.sold_qty, 0) - COALESCE(o.reserved_qty, 0),
          0
        ),
        p.sold_count = COALESCE(o.sold_qty, 0),
        p.sale_status = CASE
          WHEN p.type = 'idle' AND GREATEST(
            GREATEST(p.stock_total, COALESCE(o.sold_qty, 0) + COALESCE(o.reserved_qty, 0), 1)
              - COALESCE(o.sold_qty, 0) - COALESCE(o.reserved_qty, 0),
            0
          ) = 0 THEN 2
          ELSE 1
        END
    WHERE p.deleted = b'0';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_post' AND INDEX_NAME = 'idx_tenant_sale'
  ) THEN
    ALTER TABLE `campus_post`
      ADD KEY `idx_tenant_sale` (`tenant_id`, `type`, `status`, `sale_status`, `stock_available`);
  END IF;
END$$
DELIMITER ;
CALL `upgrade_campus_inventory`();
DROP PROCEDURE IF EXISTS `upgrade_campus_inventory`;
