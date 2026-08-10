-- Campus miniapp home configuration.
-- Safe to run repeatedly: existing administrator-managed values are preserved.

SET NAMES utf8mb4;

INSERT INTO `infra_config`
(`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'campus-home', 2, '校园首页-搜索提示', 'campus.home.search-placeholder',
       '搜索校园新鲜事', b'1', '小程序首页搜索框提示文案', 'campus', NOW(), 'campus', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `infra_config`
  WHERE `config_key` = 'campus.home.search-placeholder' AND `deleted` = b'0'
);

INSERT INTO `infra_config`
(`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'campus-home', 2, '校园首页-公告', 'campus.home.notice',
       '', b'1', '留空时首页不展示公告栏', 'campus', NOW(), 'campus', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `infra_config`
  WHERE `config_key` = 'campus.home.notice' AND `deleted` = b'0'
);

INSERT INTO `infra_config`
(`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'campus-home', 2, '校园首页-显示分类图标', 'campus.home.category-icon-visible',
       'true', b'1', 'true 显示全部分类图标；false 隐藏全部分类图标', 'campus', NOW(), 'campus', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `infra_config`
  WHERE `config_key` = 'campus.home.category-icon-visible' AND `deleted` = b'0'
);

INSERT INTO `infra_config`
(`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'campus-home', 2, '校园首页-显示分类文字', 'campus.home.category-title-visible',
       'true', b'1', 'true 显示全部分类文字；false 隐藏全部分类文字', 'campus', NOW(), 'campus', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `infra_config`
  WHERE `config_key` = 'campus.home.category-title-visible' AND `deleted` = b'0'
);

INSERT INTO `infra_config`
(`category`, `type`, `name`, `config_key`, `value`, `visible`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'campus-home', 2, '校园首页-分类导航', 'campus.home.categories',
       '[{"key":"recommend","title":"推荐","channel":"推荐","icon":"/static/images/home-prototype/category-recommend.png","iconVisible":true,"titleVisible":true},{"key":"idle","title":"二手闲置","channel":"二手","icon":"/static/images/home-prototype/category-idle.png","publishType":"idle","iconVisible":true,"titleVisible":true},{"key":"errand","title":"代拿代办","channel":"互助","icon":"/static/images/home-prototype/category-errand.png","publishType":"help","iconVisible":true,"titleVisible":true},{"key":"fun","title":"校园趣事","channel":"社团","icon":"/static/images/home-prototype/category-fun.png","publishType":"club","iconVisible":true,"titleVisible":true},{"key":"job","title":"兼职信息","channel":"兼职","icon":"/static/images/home-prototype/category-job.png","publishType":"job","iconVisible":true,"titleVisible":true},{"key":"confession","title":"表白墙","channel":"表白","icon":"💗","publishType":"confession","iconVisible":true,"titleVisible":true},{"key":"groupbuy","title":"商家团购","channel":"探店","icon":"🏪","publishType":"shop","iconVisible":true,"titleVisible":true}]',
       b'1', 'JSON 数组；支持 icon、title、iconVisible、titleVisible、channel、publishType', 'campus', NOW(), 'campus', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `infra_config`
  WHERE `config_key` = 'campus.home.categories' AND `deleted` = b'0'
);
