-- 校园闲置交易会话与卖家联系电话快照；可重复执行。
DROP PROCEDURE IF EXISTS `upgrade_campus_trade_chat`;
DELIMITER $$
CREATE PROCEDURE `upgrade_campus_trade_chat`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_trade_order'
      AND COLUMN_NAME = 'seller_phone_snapshot'
  ) THEN
    ALTER TABLE `campus_trade_order`
      ADD COLUMN `seller_phone_snapshot` varchar(20) NOT NULL DEFAULT ''
      COMMENT '卖家联系电话快照' AFTER `product_id`;
  END IF;
END$$
DELIMITER ;
CALL `upgrade_campus_trade_chat`();
DROP PROCEDURE IF EXISTS `upgrade_campus_trade_chat`;

CREATE TABLE IF NOT EXISTS `campus_trade_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '交易消息编号',
  `order_id` bigint NOT NULL COMMENT '订单编号',
  `sender_id` bigint NOT NULL COMMENT '发送用户编号',
  `receiver_id` bigint NOT NULL COMMENT '接收用户编号',
  `tenant_id` bigint NOT NULL COMMENT '校园租户编号',
  `content` varchar(500) NOT NULL COMMENT '消息内容',
  `creator` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_order_time` (`order_id`, `create_time`),
  KEY `idx_receiver_time` (`receiver_id`, `create_time`),
  KEY `idx_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园订单交易会话消息';

UPDATE `campus_trade_order` o
LEFT JOIN `campus_post` p ON p.id = o.product_id
LEFT JOIN `campus_miniapp_user` u ON u.id = o.seller_id
SET o.seller_phone_snapshot = COALESCE(NULLIF(p.contact, ''), NULLIF(u.mobile, ''), '')
WHERE o.seller_phone_snapshot = '' AND o.deleted = b'0';
