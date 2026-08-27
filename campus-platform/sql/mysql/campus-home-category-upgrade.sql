-- Dynamic homepage category management.
-- Safe to run repeatedly.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `campus_home_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类编号',
  `category_key` varchar(50) NOT NULL COMMENT '稳定分类标识',
  `title` varchar(50) NOT NULL COMMENT '小程序显示名称',
  `channel` varchar(50) NOT NULL COMMENT '对应内容频道',
  `icon_url` varchar(500) NOT NULL DEFAULT '' COMMENT '分类图标地址，支持上传图片、本地资源或 Emoji',
  `publish_type` varchar(30) NOT NULL DEFAULT '' COMMENT '跳转发布页时使用的内容类型',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `icon_visible` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否显示图标',
  `title_visible` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否显示名称',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序，数值越小越靠前',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '校区租户编号，0 表示全局配置',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_category` (`tenant_id`, `category_key`),
  KEY `idx_tenant_enabled_sort` (`tenant_id`, `enabled`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小程序首页分类入口配置';

INSERT INTO `campus_home_category`
(`category_key`, `title`, `channel`, `icon_url`, `publish_type`, `enabled`, `icon_visible`, `title_visible`, `sort`, `creator`, `updater`, `tenant_id`)
VALUES
('recommend', '推荐', '推荐', '/static/images/home-prototype/category-recommend.png', '', b'1', b'1', b'1', 10, 'campus', 'campus', 0),
('idle', '二手闲置', '二手', '/static/images/home-prototype/category-idle.png', 'idle', b'1', b'1', b'1', 20, 'campus', 'campus', 0),
('errand', '代拿代办', '互助', '/static/images/home-prototype/category-errand.png', 'help', b'1', b'1', b'1', 30, 'campus', 'campus', 0),
('fun', '校园趣事', '社团', '/static/images/home-prototype/category-fun.png', 'club', b'1', b'1', b'1', 40, 'campus', 'campus', 0),
('job', '兼职信息', '兼职', '/static/images/home-prototype/category-job.png', 'job', b'1', b'1', b'1', 50, 'campus', 'campus', 0),
('confession', '表白墙', '表白', '/static/images/home-prototype/category-confession.png', 'confession', b'1', b'1', b'1', 60, 'campus', 'campus', 0),
('groupbuy', '商家团购', '探店', '/static/images/home-prototype/category-groupbuy.png', 'shop', b'1', b'1', b'1', 70, 'campus', 'campus', 0)
ON DUPLICATE KEY UPDATE
  `title` = `title`,
  `update_time` = `update_time`;
