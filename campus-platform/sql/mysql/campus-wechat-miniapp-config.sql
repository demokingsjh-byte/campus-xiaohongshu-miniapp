-- WeChat mini program configuration for Campus Xiaohongshu.
-- Run after sql/mysql/ruoyi-vue-pro-cloud.sql and sql/mysql/campus-extension.sql.
-- It can also be executed on an already initialized database to refresh the miniapp AppID.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @miniapp_appid = _utf8mb4'wx3141ca27b55b0b9c' COLLATE utf8mb4_unicode_ci;
SET @miniapp_secret = _utf8mb4'34faf6475497b6c5c9ad777b725a2ed3' COLLATE utf8mb4_unicode_ci;
SET @old_miniapp_appid_1 = _utf8mb4'wx63c280fe3248a3e7' COLLATE utf8mb4_unicode_ci;
SET @old_miniapp_appid_2 = _utf8mb4'wxc4598c446f8a9cb3' COLLATE utf8mb4_unicode_ci;

UPDATE `system_social_client`
SET `name` = '微信小程序',
    `client_id` = @miniapp_appid,
    `client_secret` = @miniapp_secret,
    `status` = 0,
    `deleted` = b'0',
    `updater` = '1',
    `update_time` = NOW()
WHERE `social_type` = 34
  AND `user_type` = 1
  AND `tenant_id` IN (1, 201, 202);

INSERT INTO `system_social_client`
(`name`, `social_type`, `user_type`, `client_id`, `client_secret`, `agent_id`, `public_key`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '微信小程序', 34, 1, @miniapp_appid, @miniapp_secret, NULL, NULL, 0, '1', NOW(), '1', NOW(), b'0', `tenant`.`id`
FROM `system_tenant` AS `tenant`
WHERE `tenant`.`id` IN (1, 201, 202)
  AND `tenant`.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_social_client` AS `client`
    WHERE `client`.`social_type` = 34
      AND `client`.`user_type` = 1
      AND `client`.`tenant_id` = `tenant`.`id`
  );

UPDATE `system_tenant`
SET `websites` = TRIM(BOTH ',' FROM REPLACE(REPLACE(CONCAT(',', IFNULL(`websites`, ''), ','), CONCAT(',', @old_miniapp_appid_1, ','), ','), CONCAT(',', @old_miniapp_appid_2, ','), ','))
WHERE `id` IN (1, 201, 202);

UPDATE `system_tenant`
SET `websites` = CASE
  WHEN `websites` IS NULL OR `websites` = '' THEN @miniapp_appid
  WHEN FIND_IN_SET(@miniapp_appid, `websites`) = 0 THEN CONCAT(`websites`, ',', @miniapp_appid)
  ELSE `websites`
END
WHERE `id` IN (1, 201, 202);
