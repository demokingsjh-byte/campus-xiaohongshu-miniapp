-- Existing database upgrade: student profile fields and admin menu naming.
-- Run after campus-extension.sql. The script is idempotent.

SET NAMES utf8mb4;

SET @campus_schema = DATABASE();

SET @campus_sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @campus_schema AND TABLE_NAME = 'campus_miniapp_user' AND COLUMN_NAME = 'grade') = 0,
  'ALTER TABLE `campus_miniapp_user` ADD COLUMN `grade` varchar(32) NOT NULL DEFAULT '''' COMMENT ''年级'' AFTER `campus_name`',
  'SELECT 1'
);
PREPARE campus_stmt FROM @campus_sql;
EXECUTE campus_stmt;
DEALLOCATE PREPARE campus_stmt;

SET @campus_sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @campus_schema AND TABLE_NAME = 'campus_miniapp_user' AND COLUMN_NAME = 'gender') = 0,
  'ALTER TABLE `campus_miniapp_user` ADD COLUMN `gender` varchar(16) NOT NULL DEFAULT ''不公开'' COMMENT ''性别：不公开、男、女'' AFTER `grade`',
  'SELECT 1'
);
PREPARE campus_stmt FROM @campus_sql;
EXECUTE campus_stmt;
DEALLOCATE PREPARE campus_stmt;

SET @campus_sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = @campus_schema AND TABLE_NAME = 'campus_miniapp_user' AND INDEX_NAME = 'idx_school_campus') = 0,
  'ALTER TABLE `campus_miniapp_user` ADD KEY `idx_school_campus` (`school_name`, `campus_name`)',
  'SELECT 1'
);
PREPARE campus_stmt FROM @campus_sql;
EXECUTE campus_stmt;
DEALLOCATE PREPARE campus_stmt;

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
