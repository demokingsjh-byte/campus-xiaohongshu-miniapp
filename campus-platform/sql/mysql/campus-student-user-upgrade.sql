-- Existing database upgrade: student profile fields and admin menu naming.
-- Run once after campus-extension.sql has already been applied.

SET NAMES utf8mb4;

ALTER TABLE `campus_miniapp_user`
  ADD COLUMN `grade` varchar(32) NOT NULL DEFAULT '' COMMENT '年级' AFTER `campus_name`,
  ADD COLUMN `gender` varchar(16) NOT NULL DEFAULT '不公开' COMMENT '性别：不公开、男、女' AFTER `grade`,
  ADD KEY `idx_school_campus` (`school_name`, `campus_name`);

UPDATE `system_menu`
SET `name` = '学生用户', `component_name` = 'CampusStudentUser', `update_time` = NOW()
WHERE `id` = 900600;

UPDATE `system_menu`
SET `name` = CASE `id`
  WHEN 900601 THEN '学生用户修改'
  WHEN 900602 THEN '学生用户删除'
  ELSE `name`
END, `update_time` = NOW()
WHERE `id` IN (900601, 900602);
