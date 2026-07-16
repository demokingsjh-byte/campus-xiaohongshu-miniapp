-- Repair menu names in databases initialized from an incorrectly encoded SQL file.
-- Safe to run repeatedly. Run campus-menu.sql afterwards to refresh campus menus.

SET NAMES utf8mb4;

-- Older databases may have been created with a latin1 default. In that case
-- even a UTF-8 migration connection stores Chinese menu names as question
-- marks. Normalize the two user-facing metadata tables before rewriting data.
ALTER TABLE `system_menu`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `system_dict_data`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE `system_menu`
SET `name` = CASE `id`
  WHEN 1 THEN '系统管理'
  WHEN 2 THEN '基础设施'
  WHEN 100 THEN '用户管理'
  WHEN 101 THEN '角色管理'
  WHEN 102 THEN '菜单管理'
  WHEN 103 THEN '部门管理'
  WHEN 104 THEN '岗位管理'
  WHEN 105 THEN '字典管理'
  WHEN 106 THEN '配置管理'
  WHEN 107 THEN '通知公告'
  WHEN 108 THEN '审计日志'
  WHEN 110 THEN '定时任务'
  WHEN 114 THEN '表单构建'
  WHEN 115 THEN '代码生成'
  WHEN 116 THEN 'API 接口'
  WHEN 500 THEN '操作日志'
  WHEN 501 THEN '登录日志'
  WHEN 1040 THEN '操作查询'
  WHEN 1042 THEN '日志导出'
  WHEN 900000 THEN '校园运营'
  WHEN 900050 THEN '数据看板'
  WHEN 900100 THEN '区域管理'
  WHEN 900101 THEN '区域新增'
  WHEN 900102 THEN '区域修改'
  WHEN 900103 THEN '区域删除'
  WHEN 900200 THEN '学校资料'
  WHEN 900201 THEN '学校新增'
  WHEN 900202 THEN '学校修改'
  WHEN 900203 THEN '学校删除'
  WHEN 900300 THEN '校区租户'
  WHEN 900301 THEN '校区新增'
  WHEN 900302 THEN '校区修改'
  WHEN 900303 THEN '校区删除'
  WHEN 900400 THEN '校区代理'
  WHEN 900401 THEN '代理新增'
  WHEN 900402 THEN '代理修改'
  WHEN 900403 THEN '代理删除'
  WHEN 900500 THEN '商品管理'
  WHEN 900501 THEN '商品新增'
  WHEN 900502 THEN '商品修改'
  WHEN 900503 THEN '商品删除'
  WHEN 900600 THEN '学生用户'
  WHEN 900601 THEN '学生用户修改'
  WHEN 900602 THEN '学生用户删除'
  WHEN 900700 THEN '数据日志'
  WHEN 900701 THEN '日志导出'
  WHEN 900800 THEN '内容管理'
  WHEN 900801 THEN '内容修改'
  WHEN 900802 THEN '内容删除'
  ELSE `name`
END,
`updater` = 'campus',
`update_time` = NOW()
WHERE `id` IN (1, 2, 100, 101, 102, 103, 104, 105, 106, 107, 108, 110, 114, 115, 116,
               500, 501, 1040, 1042, 900000, 900050, 900100, 900101, 900102, 900103,
               900200, 900201, 900202, 900203, 900300, 900301, 900302, 900303,
               900400, 900401, 900402, 900403, 900500, 900501, 900502, 900503,
               900600, 900601, 900602, 900700, 900701, 900800, 900801, 900802);

UPDATE `system_dict_data`
SET `label` = CASE `id`
  WHEN 29 THEN '目录'
  WHEN 30 THEN '菜单'
  WHEN 31 THEN '按钮'
  ELSE `label`
END,
`remark` = CASE `id`
  WHEN 29 THEN '目录'
  WHEN 30 THEN '菜单'
  WHEN 31 THEN '按钮'
  ELSE `remark`
END,
`update_time` = NOW()
WHERE `id` IN (29, 30, 31);

-- Historical rows outside the maintained ID mapping cannot be restored safely.
-- Quarantine them so broken labels are never exposed in the admin navigation.
-- campus-menu.sql runs next and re-enables every campus menu maintained by code.
UPDATE `system_menu`
SET `status` = 1,
    `visible` = b'0',
    `updater` = 'campus',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `visible` = b'1'
  AND `name` REGEXP '[?？]{2,}';
