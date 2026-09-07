-- 小程序“我的 - 我的交易”动态开关。
-- 已有环境执行本文件；可重复执行，不会覆盖管理员已经调整的开关状态。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `campus_mine_trade_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置编号',
  `config_name` varchar(64) NOT NULL DEFAULT '全局配置' COMMENT '配置名称',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '我的交易总开关',
  `published_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否显示已发布',
  `sold_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否显示已卖出',
  `bought_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否显示已买到',
  `pending_payment_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否显示待支付',
  `paid_enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否显示已支付',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '校区租户编号，0表示全局',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mine_trade_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小程序我的交易入口配置';

INSERT INTO `campus_mine_trade_config`
(`config_name`, `enabled`, `published_enabled`, `sold_enabled`, `bought_enabled`, `pending_payment_enabled`, `paid_enabled`, `creator`, `updater`, `tenant_id`)
VALUES ('全局配置', b'1', b'1', b'1', b'1', b'1', b'1', 'campus', 'campus', 0)
ON DUPLICATE KEY UPDATE `config_name` = `config_name`;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900760, '交易开关', 'campus:mine-trade-config:query', 2, 9, 900000, 'mine-trade-config', 'ep:switch-button',
     'campus/base/index', 'CampusMineTradeConfig', 0, b'1', b'1', b'1',
     'campus', NOW(), 'campus', NOW(), b'0'),
    (900761, '交易开关修改', 'campus:mine-trade-config:update', 3, 1, 900760, '', '', '', NULL,
     0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), permission = VALUES(permission), sort = VALUES(sort),
    parent_id = VALUES(parent_id), path = VALUES(path), icon = VALUES(icon),
    component = VALUES(component), component_name = VALUES(component_name),
    status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';

-- 已有“分类管理”权限的角色自动获得交易开关的查看和修改权限。
INSERT INTO system_role_menu
    (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT source_role.role_id, target_menu.menu_id, 'campus', NOW(), 'campus', NOW(), b'0', source_role.tenant_id
FROM system_role_menu source_role
CROSS JOIN (
  SELECT 900760 AS menu_id
  UNION ALL SELECT 900761
) target_menu
WHERE source_role.menu_id = 900750
  AND source_role.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM system_role_menu existing_role
    WHERE existing_role.role_id = source_role.role_id
      AND existing_role.menu_id = target_menu.menu_id
      AND existing_role.tenant_id = source_role.tenant_id
      AND existing_role.deleted = b'0'
  );
