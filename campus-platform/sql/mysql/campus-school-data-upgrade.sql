-- Add the first two Hunan campuses used by the mini program.
-- Run after campus-extension.sql. The script is idempotent.

SET NAMES utf8mb4;

INSERT INTO `system_tenant`
(`id`, `name`, `contact_user_id`, `contact_name`, `contact_mobile`, `status`, `websites`, `package_id`, `expire_time`, `account_count`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(201, '吉首大学', NULL, '湘西校园站', '', 0, '', 0, '2099-12-31 23:59:59', 50, 'campus', NOW(), 'campus', NOW(), b'0'),
(202, '长沙学院', NULL, '长沙校园站', '', 0, '', 0, '2099-12-31 23:59:59', 50, 'campus', NOW(), 'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `status` = 0, `expire_time` = VALUES(`expire_time`),
  `updater` = 'campus', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `campus_region`
(`id`, `name`, `province`, `city`, `district`, `status`, `sort`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(201, '湘西高校圈', '湖南省', '吉首市', '吉首市', 0, 40, 'campus', NOW(), 'campus', NOW(), b'0', 0),
(202, '长沙高校圈', '湖南省', '长沙市', '开福区', 0, 50, 'campus', NOW(), 'campus', NOW(), b'0', 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `province` = VALUES(`province`), `city` = VALUES(`city`),
  `district` = VALUES(`district`), `status` = 0, `sort` = VALUES(`sort`),
  `updater` = 'campus', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `campus_school_catalog`
(`id`, `name`, `province`, `city`, `district`, `logo_url`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(201, '吉首大学', '湖南省', '吉首市', '吉首市', '', 0, 'campus', NOW(), 'campus', NOW(), b'0', 0),
(202, '长沙学院', '湖南省', '长沙市', '开福区', '', 0, 'campus', NOW(), 'campus', NOW(), b'0', 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `province` = VALUES(`province`), `city` = VALUES(`city`),
  `district` = VALUES(`district`), `status` = 0,
  `updater` = 'campus', `update_time` = NOW(), `deleted` = b'0';

INSERT INTO `campus_tenant_profile`
(`id`, `system_tenant_id`, `region_id`, `school_id`, `school_name`, `campus_name`, `display_name`, `logo_url`, `province`, `city`, `district`, `address`, `agent_user_id`, `commission_enabled`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
VALUES
(201, 201, 201, 201, '吉首大学', '吉首校区', '吉首大学', '', '湖南省', '吉首市', '吉首市', '', 0, b'1', 1, 'campus', NOW(), 'campus', NOW(), b'0', 201),
(202, 202, 202, 202, '长沙学院', '主校区', '长沙学院', '', '湖南省', '长沙市', '开福区', '', 0, b'1', 1, 'campus', NOW(), 'campus', NOW(), b'0', 202)
ON DUPLICATE KEY UPDATE
  `region_id` = VALUES(`region_id`), `school_id` = VALUES(`school_id`),
  `school_name` = VALUES(`school_name`), `campus_name` = VALUES(`campus_name`),
  `display_name` = VALUES(`display_name`), `province` = VALUES(`province`),
  `city` = VALUES(`city`), `district` = VALUES(`district`), `status` = 1,
  `updater` = 'campus', `update_time` = NOW(), `deleted` = b'0';
