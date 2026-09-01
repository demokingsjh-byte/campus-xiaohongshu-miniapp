-- 代拿代办无需人工审核：修复升级前因异步内容安全检测而停留在待审核状态的任务。
-- 已确认违规（RISKY）的记录不会被重新发布；异步回调后续仍可自动下架违规内容。
SET NAMES utf8mb4;

UPDATE `campus_post` p
SET p.`status` = 1,
    p.`updater` = 'errand-audit-upgrade',
    p.`update_time` = NOW()
WHERE p.`type` = 'help'
  AND p.`status` = 0
  AND p.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `campus_content_audit` a
    WHERE a.`entity_type` = 'POST'
      AND a.`entity_id` = p.`id`
      AND a.`suggest` = 'RISKY'
      AND a.`deleted` = b'0'
  );
