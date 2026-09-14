-- ESP32 助手 WebSocket 链路耗时日志。
-- 只记录设备、请求和阶段耗时，不保存 device_token、音频、图片或对话原文。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS campus_esp32_assistant_log (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '日志编号',
  session_id varchar(64) NOT NULL COMMENT 'WebSocket 会话编号',
  device_id varchar(64) NOT NULL COMMENT '设备编号',
  request_id varchar(100) NOT NULL COMMENT '轮次请求编号',
  client_ip varchar(64) NOT NULL DEFAULT '' COMMENT '客户端 IP',
  status varchar(24) NOT NULL DEFAULT 'CAPTURING' COMMENT '链路状态',
  audio_bytes int unsigned NOT NULL DEFAULT 0 COMMENT '音频字节数',
  image_count tinyint unsigned NOT NULL DEFAULT 0 COMMENT '图片数量',
  capture_ms bigint DEFAULT NULL COMMENT '采集耗时（turn_start 到 turn_commit）',
  submit_ms bigint DEFAULT NULL COMMENT '提交模型耗时',
  asr_ms bigint DEFAULT NULL COMMENT 'ASR 耗时',
  model_first_token_ms bigint DEFAULT NULL COMMENT '模型首 token 耗时（上游统计）',
  model_total_ms bigint DEFAULT NULL COMMENT '模型总耗时（上游统计）',
  tts_first_audio_ms bigint DEFAULT NULL COMMENT 'TTS 首包耗时（从本轮开始）',
  tts_audio_ms bigint DEFAULT NULL COMMENT 'TTS 音频输出耗时',
  total_ms bigint DEFAULT NULL COMMENT '本轮总耗时',
  error_code varchar(64) DEFAULT NULL COMMENT '错误码或忽略原因',
  error_message varchar(255) DEFAULT NULL COMMENT '错误说明',
  creator varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NOT NULL DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0 COMMENT '平台级日志',
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_request (session_id, request_id),
  KEY idx_device_time (device_id, create_time),
  KEY idx_status_time (status, create_time),
  KEY idx_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ESP32 助手链路耗时日志';

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
VALUES
    (900960, 'ESP32链路日志', 'campus:esp32-log:query', 2, 12, 900000, 'esp32-log', 'ep:connection',
     'campus/esp32-log/index', 'CampusEsp32Log', 0, b'1', b'1', b'1', 'campus', NOW(),
     'campus', NOW(), b'0')
ON DUPLICATE KEY UPDATE name = VALUES(name), permission = VALUES(permission), sort = VALUES(sort),
    parent_id = VALUES(parent_id), path = VALUES(path), icon = VALUES(icon), component = VALUES(component),
    component_name = VALUES(component_name), status = 0, visible = b'1', updater = 'campus',
    update_time = NOW(), deleted = b'0';

INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 1, 900960, 'campus', NOW(), 'campus', NOW(), b'0', 0
WHERE NOT EXISTS (
    SELECT 1 FROM system_role_menu
    WHERE role_id = 1 AND menu_id = 900960 AND deleted = b'0'
);
