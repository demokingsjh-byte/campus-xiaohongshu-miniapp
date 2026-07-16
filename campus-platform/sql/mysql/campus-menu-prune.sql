-- Hide non-MVP RuoYi/Yudao menus for the campus project.
-- This script is non-destructive: it disables and hides menus instead of deleting them.
-- Run after the UTF-8 ruoyi-vue-pro-cloud-nobom.sql and campus-extension.sql.

SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS campus_menu_hide;
DROP TEMPORARY TABLE IF EXISTS campus_menu_next;
CREATE TEMPORARY TABLE campus_menu_hide (
  id bigint NOT NULL PRIMARY KEY
);
CREATE TEMPORARY TABLE campus_menu_next (
  id bigint NOT NULL PRIMARY KEY
);

-- Top-level product/demo/document modules not needed by the campus MVP.
INSERT IGNORE INTO campus_menu_hide (id) VALUES
  (1254), -- 作者动态
  (2159), -- Boot 开发文档
  (2160), -- Cloud 开发文档
  (1117), -- 支付管理
  (1281), -- 报表管理
  (1185), -- 工作流程
  (2262), -- 会员中心
  (2362), -- 商城系统
  (2084), -- 公众号管理
  (2397), -- CRM 系统
  (2563), -- ERP 系统
  (6400), -- WMS 系统
  (5100), -- MES 系统
  (2758), -- AI 大模型
  (4000), -- IoT 物联网
  (6500); -- IM 即时通讯

-- System menus that are not needed in the early campus admin console.
INSERT IGNORE INTO campus_menu_hide (id) VALUES
  (104),  -- 岗位管理
  (2739), -- 消息中心
  (1261), -- OAuth 2.0
  (2447); -- 三方登录

-- Infra menus kept for later can be restored from 菜单管理; hide noisy dev/monitor entries for now.
INSERT IGNORE INTO campus_menu_hide (id) VALUES
  (1070), -- 代码生成案例
  (114),  -- 表单构建
  (1083), -- API 日志
  (2525), -- WebSocket
  (110),  -- 定时任务
  (2740); -- 监控中心

-- Hide legacy campus entries whose names were permanently stored as question marks.
-- Limit the match to direct children of 校园运营 so unrelated menus are untouched.
INSERT IGNORE INTO campus_menu_hide (id)
SELECT id
FROM system_menu
WHERE parent_id = 900000
  AND deleted = b'0'
  AND TRIM(name) REGEXP '^[?]+$';

-- Include descendants. Repeat a few times because MySQL 5.7 has no recursive CTE.
TRUNCATE TABLE campus_menu_next;
INSERT IGNORE INTO campus_menu_next (id)
SELECT m.id
FROM system_menu m
JOIN campus_menu_hide h ON m.parent_id = h.id;
INSERT IGNORE INTO campus_menu_hide (id)
SELECT id FROM campus_menu_next;

TRUNCATE TABLE campus_menu_next;
INSERT IGNORE INTO campus_menu_next (id)
SELECT m.id
FROM system_menu m
JOIN campus_menu_hide h ON m.parent_id = h.id;
INSERT IGNORE INTO campus_menu_hide (id)
SELECT id FROM campus_menu_next;

TRUNCATE TABLE campus_menu_next;
INSERT IGNORE INTO campus_menu_next (id)
SELECT m.id
FROM system_menu m
JOIN campus_menu_hide h ON m.parent_id = h.id;
INSERT IGNORE INTO campus_menu_hide (id)
SELECT id FROM campus_menu_next;

TRUNCATE TABLE campus_menu_next;
INSERT IGNORE INTO campus_menu_next (id)
SELECT m.id
FROM system_menu m
JOIN campus_menu_hide h ON m.parent_id = h.id;
INSERT IGNORE INTO campus_menu_hide (id)
SELECT id FROM campus_menu_next;

UPDATE system_menu m
JOIN campus_menu_hide h ON m.id = h.id
SET m.status = 1,
    m.visible = b'0',
    m.updater = 'campus',
    m.update_time = NOW();

DROP TEMPORARY TABLE IF EXISTS campus_menu_hide;
DROP TEMPORARY TABLE IF EXISTS campus_menu_next;
