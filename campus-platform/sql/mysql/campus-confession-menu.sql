-- 表白墙后台管理菜单。已有校园运营菜单后可单独执行本文件。
SET NAMES utf8mb4;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900850, '表白管理', 'campus:post:query', 2, 9, 900000, 'confession', 'ep:chat-line-round',
     'campus/confession/index', 'CampusConfession', 0, b'1', b'1', b'1',
     'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), permission = VALUES(permission), sort = VALUES(sort),
    parent_id = VALUES(parent_id), path = VALUES(path), icon = VALUES(icon),
    component = VALUES(component), component_name = VALUES(component_name),
    status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';

-- 将菜单授权给已经拥有“内容管理”菜单的角色。
INSERT INTO system_role_menu
    (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT DISTINCT source_role.role_id, 900850, 'campus', NOW(), 'campus', NOW(), b'0', source_role.tenant_id
FROM system_role_menu source_role
WHERE source_role.menu_id = 900800
  AND source_role.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1
      FROM system_role_menu target_role
      WHERE target_role.role_id = source_role.role_id
        AND target_role.menu_id = 900850
        AND target_role.tenant_id = source_role.tenant_id
        AND target_role.deleted = b'0'
  );
