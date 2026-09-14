-- ESP32 助手对话内容留存升级。
--
-- 仅供拥有 campus:esp32-log:query 权限的管理后台查看，设备 Token、原始音频仍不会落库。
-- 图片按日志分行存储，便于审计、详情查看和后续清理；接口不会把图片地址暴露给小程序端。
SET NAMES utf8mb4;

-- 这几个字段用动态 DDL，保证已经执行过的线上版本可以重复发布。
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'content_recorded') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN content_recorded bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否启用内容留存'' AFTER image_count',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'asr_status') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN asr_status varchar(16) DEFAULT NULL COMMENT ''ASR 状态'' AFTER asr_ms',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'question_text') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN question_text mediumtext DEFAULT NULL COMMENT ''用户提问（ASR 转写）'' AFTER asr_status',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campus_esp32_assistant_log'
      AND COLUMN_NAME = 'answer_text') = 0,
  'ALTER TABLE campus_esp32_assistant_log ADD COLUMN answer_text mediumtext DEFAULT NULL COMMENT ''模型最终回答'' AFTER question_text',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS campus_esp32_assistant_log_image (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '图片编号',
  log_id bigint NOT NULL COMMENT '链路日志编号',
  image_index tinyint unsigned NOT NULL COMMENT '本轮图片序号（从 0 开始）',
  mime_type varchar(64) NOT NULL DEFAULT 'image/jpeg' COMMENT '图片 MIME 类型',
  size_bytes int unsigned NOT NULL DEFAULT 0 COMMENT '图片字节数',
  image_data mediumblob NOT NULL COMMENT '原始图片二进制（仅后台权限可读）',
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0 COMMENT '平台级日志',
  PRIMARY KEY (id),
  UNIQUE KEY uk_log_image (log_id, image_index),
  KEY idx_log_id (log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ESP32 助手用户图片留存';
