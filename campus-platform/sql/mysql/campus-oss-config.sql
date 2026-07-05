-- Aliyun OSS file storage configuration for Campus Xiaohongshu.
-- Run after sql/mysql/ruoyi-vue-pro-cloud.sql.
-- It can also be executed on an already initialized database.

SET NAMES utf8mb4;

SET @oss_name = 'Aliyun OSS - Yundian Campus';
SET @oss_config = JSON_OBJECT(
  '@class', 'cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClientConfig',
  'endpoint', 'oss-cn-shenzhen.aliyuncs.com',
  'domain', 'https://dylsjh.oss-cn-shenzhen.aliyuncs.com',
  'bucket', 'dylsjh',
  'accessKey', 'REPLACE_WITH_ALI_OSS_ACCESS_KEY_ID',
  'accessSecret', 'REPLACE_WITH_ALI_OSS_ACCESS_KEY_SECRET',
  'enablePathStyleAccess', false,
  'enablePublicAccess', false,
  'region', 'cn-shenzhen'
);

UPDATE `infra_file_config`
SET `storage` = 20,
    `remark` = 'commerce-images/',
    `master` = b'1',
    `config` = @oss_config,
    `updater` = '1',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `name` = @oss_name;

INSERT INTO `infra_file_config`
(`name`, `storage`, `remark`, `master`, `config`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT @oss_name, 20, 'commerce-images/', b'1', @oss_config, '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1
  FROM `infra_file_config`
  WHERE `name` = @oss_name
);

SET @oss_config_id = (
  SELECT `id`
  FROM `infra_file_config`
  WHERE `name` = @oss_name
  ORDER BY `id` DESC
  LIMIT 1
);

UPDATE `infra_file_config`
SET `master` = IF(`id` = @oss_config_id, b'1', b'0'),
    `update_time` = NOW()
WHERE `deleted` = b'0';
