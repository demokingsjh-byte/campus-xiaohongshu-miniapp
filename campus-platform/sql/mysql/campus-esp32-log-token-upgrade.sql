-- ESP32 每轮上游实际 Token 用量；幂等添加字段，旧记录保留 NULL。
-- 不回填/估算历史数据，不保存 API Key、原始音频或完整响应体。
SET NAMES utf8mb4;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'usage_status') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN usage_status varchar(16) DEFAULT NULL COMMENT ''Token状态：PENDING/REPORTED/UNAVAILABLE；旧记录为空''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'usage_response_id') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN usage_response_id varchar(128) DEFAULT NULL COMMENT ''用量对应的上游响应ID''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'input_tokens') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN input_tokens bigint unsigned DEFAULT NULL COMMENT ''上游输入Token含历史''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'output_tokens') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN output_tokens bigint unsigned DEFAULT NULL COMMENT ''上游输出Token''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'total_tokens') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN total_tokens bigint unsigned DEFAULT NULL COMMENT ''上游总Token不推算''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'input_text_tokens') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN input_text_tokens bigint unsigned DEFAULT NULL COMMENT ''上游文本输入Token''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'input_audio_tokens') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN input_audio_tokens bigint unsigned DEFAULT NULL COMMENT ''上游音频输入Token''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'input_image_tokens') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN input_image_tokens bigint unsigned DEFAULT NULL COMMENT ''上游图片输入Token''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'input_video_tokens') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN input_video_tokens bigint unsigned DEFAULT NULL COMMENT ''上游视频输入Token''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'input_cached_tokens') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN input_cached_tokens bigint unsigned DEFAULT NULL COMMENT ''上游缓存输入Token为输入子集''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'output_text_tokens') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN output_text_tokens bigint unsigned DEFAULT NULL COMMENT ''上游文本输出Token''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'output_audio_tokens') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN output_audio_tokens bigint unsigned DEFAULT NULL COMMENT ''上游音频输出Token''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'usage_json') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN usage_json mediumtext DEFAULT NULL COMMENT ''规范化数字用量详情不含问答及媒体''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
