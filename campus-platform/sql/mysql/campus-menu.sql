-- Campus admin menus and permissions.
-- Run after ruoyi-vue-pro-cloud-nobom.sql, campus-extension.sql and campus-menu-prune.sql.

SET NAMES utf8mb4;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900000, '校园运营', '', 1, 5, 0, '/campus', 'ep:school', NULL, NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE name = VALUES(name), sort = VALUES(sort), parent_id = VALUES(parent_id), path = VALUES(path), icon = VALUES(icon), status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900100, '区域管理', 'campus:region:query', 2, 1, 900000, 'region', 'ep:map-location', 'campus/base/index', 'CampusRegion', 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900200, '学校资料', 'campus:school:query', 2, 2, 900000, 'school', 'ep:office-building', 'campus/base/index', 'CampusSchool', 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900300, '校区租户', 'campus:tenant-profile:query', 2, 3, 900000, 'tenant-profile', 'ep:coordinate', 'campus/base/index', 'CampusTenantProfile', 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900400, '校区代理', 'campus:agent:query', 2, 4, 900000, 'agent', 'ep:user-filled', 'campus/base/index', 'CampusAgent', 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900500, '商品管理', 'campus:product:query', 2, 5, 900000, 'product', 'ep:goods', 'campus/base/index', 'CampusProduct', 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900600, '学生用户', 'campus:miniapp-user:query', 2, 6, 900000, 'miniapp-user', 'ep:user', 'campus/base/index', 'CampusStudentUser', 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), sort = VALUES(sort), parent_id = VALUES(parent_id), path = VALUES(path), icon = VALUES(icon), component = VALUES(component), component_name = VALUES(component_name), status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900101, '区域新增', 'campus:region:create', 3, 1, 900100, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900102, '区域修改', 'campus:region:update', 3, 2, 900100, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900103, '区域删除', 'campus:region:delete', 3, 3, 900100, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900201, '学校新增', 'campus:school:create', 3, 1, 900200, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900202, '学校修改', 'campus:school:update', 3, 2, 900200, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900203, '学校删除', 'campus:school:delete', 3, 3, 900200, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900301, '校区新增', 'campus:tenant-profile:create', 3, 1, 900300, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900302, '校区修改', 'campus:tenant-profile:update', 3, 2, 900300, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900303, '校区删除', 'campus:tenant-profile:delete', 3, 3, 900300, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900401, '代理新增', 'campus:agent:create', 3, 1, 900400, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900402, '代理修改', 'campus:agent:update', 3, 2, 900400, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900403, '代理删除', 'campus:agent:delete', 3, 3, 900400, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900501, '商品新增', 'campus:product:create', 3, 1, 900500, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900502, '商品修改', 'campus:product:update', 3, 2, 900500, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900503, '商品删除', 'campus:product:delete', 3, 3, 900500, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900601, '学生用户修改', 'campus:miniapp-user:update', 3, 1, 900600, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0'),
    (900602, '学生用户删除', 'campus:miniapp-user:delete', 3, 2, 900600, '', '', '', NULL, 0, b'1', b'1', b'1', 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), parent_id = VALUES(parent_id), sort = VALUES(sort), status = 0, visible = b'1', updater = 'campus', update_time = NOW(), deleted = b'0';
