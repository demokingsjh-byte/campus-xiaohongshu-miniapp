-- Repair menu names in databases initialized from an incorrectly encoded SQL file.
-- Safe to run repeatedly. Run campus-menu.sql afterwards to refresh campus menus.

SET NAMES utf8mb4;

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
  WHEN 900100 THEN '区域管理'
  WHEN 900200 THEN '学校资料'
  WHEN 900300 THEN '校区租户'
  WHEN 900400 THEN '校区代理'
  WHEN 900500 THEN '商品管理'
  WHEN 900600 THEN '学生用户'
  WHEN 900700 THEN '数据日志'
  ELSE `name`
END,
`updater` = 'campus',
`update_time` = NOW()
WHERE `id` IN (1, 2, 100, 101, 102, 103, 104, 105, 106, 107, 108, 110, 114, 115, 116,
               500, 501, 1040, 1042, 900000, 900100, 900200, 900300, 900400, 900500, 900600, 900700);

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
