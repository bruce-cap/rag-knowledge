USE `rag`;

-- =========================================
-- P0 / P1: 当前建议直接落地的数据库变更
-- =========================================

-- 1. 会话表增强：AI 标题、手动标题保护、置顶
ALTER TABLE `chat_session`
    MODIFY COLUMN `title` varchar(255) DEFAULT '新的会话' COMMENT '会话标题，可由AI生成',
    ADD COLUMN `is_title_manual` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已手动修改标题：0-否，1-是' AFTER `title`,
    ADD COLUMN `is_pinned` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否，1-是' AFTER `is_deleted`,
    ADD COLUMN `pin_time` datetime DEFAULT NULL COMMENT '置顶时间' AFTER `is_pinned`;

CREATE INDEX `idx_chat_session_user_pinned_update`
    ON `chat_session` (`user_id`, `is_deleted`, `is_pinned`, `pin_time`, `update_time`);

-- 2. 用户表增强：启停、个人资料
ALTER TABLE `sys_user`
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '用户状态：1-启用，0-禁用' AFTER `role`,
    ADD COLUMN `nickname` varchar(100) DEFAULT NULL COMMENT '用户昵称' AFTER `email`,
    ADD COLUMN `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址' AFTER `nickname`,
    ADD COLUMN `phone` varchar(20) DEFAULT NULL COMMENT '手机号' AFTER `avatar`,
    ADD COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `status`;

CREATE INDEX `idx_sys_user_status` ON `sys_user` (`status`);
CREATE INDEX `idx_sys_user_phone` ON `sys_user` (`phone`);

-- 3. 空间成员权限模型升级：ADMIN / MEMBER / VIEW
UPDATE `space_member`
SET `role` = UPPER(`role`)
WHERE `role` IS NOT NULL;

ALTER TABLE `space_member`
    MODIFY COLUMN `role` varchar(20) NOT NULL DEFAULT 'VIEW' COMMENT '空间角色：ADMIN / MEMBER / VIEW';

-- 如果你希望当前已有 MEMBER 保持可上传，不要执行下面这句。
-- 如果你希望所有普通成员立即切到只读，再手工审批升级，可取消注释执行。
-- UPDATE `space_member`
-- SET `role` = 'VIEW'
-- WHERE `role` = 'MEMBER';

-- ALTER TABLE `space_member`
--     ADD CONSTRAINT `chk_space_member_role`
--         CHECK (`role` IN ('ADMIN', 'MEMBER', 'VIEW'));

-- 4. 上传权限申请：用“角色升级申请”替代 can_upload 字段
DROP TABLE IF EXISTS `space_role_request`;
CREATE TABLE `space_role_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `space_id` bigint NOT NULL COMMENT '目标空间ID',
  `user_id` bigint NOT NULL COMMENT '申请用户ID',
  `current_role` varchar(20) NOT NULL DEFAULT 'VIEW' COMMENT '申请时当前空间角色',
  `target_role` varchar(20) NOT NULL DEFAULT 'MEMBER' COMMENT '目标角色，当前用于申请上传权限',
  `request_type` varchar(30) NOT NULL DEFAULT 'UPLOAD_PERMISSION' COMMENT '申请类型：UPLOAD_PERMISSION / ROLE_UPGRADE',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / APPROVED / REJECTED / CANCELED',
  `apply_reason` varchar(500) DEFAULT NULL COMMENT '申请原因',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '审批意见',
  `review_by` bigint DEFAULT NULL COMMENT '审批人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审批时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_space_role_request_space_id` (`space_id`),
  KEY `idx_space_role_request_user_id` (`user_id`),
  KEY `idx_space_role_request_status` (`status`),
  KEY `idx_space_role_request_type` (`request_type`),
  KEY `idx_space_role_request_space_user_status` (`space_id`, `user_id`, `status`),
  KEY `fk_space_role_request_review_user` (`review_by`),
  CONSTRAINT `fk_space_role_request_space`
      FOREIGN KEY (`space_id`) REFERENCES `knowledge_space` (`id`),
  CONSTRAINT `fk_space_role_request_user`
      FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_space_role_request_review_user`
      FOREIGN KEY (`review_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_space_role_request_current_role`
      CHECK (`current_role` IN ('ADMIN', 'MEMBER', 'VIEW')),
  CONSTRAINT `chk_space_role_request_target_role`
      CHECK (`target_role` IN ('ADMIN', 'MEMBER', 'VIEW')),
  CONSTRAINT `chk_space_role_request_type`
      CHECK (`request_type` IN ('UPLOAD_PERMISSION', 'ROLE_UPGRADE')),
  CONSTRAINT `chk_space_role_request_status`
      CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='空间角色申请表';

-- 5. 文档表增强：预览、下载、失败重试
ALTER TABLE `document`
    ADD COLUMN `mime_type` varchar(100) DEFAULT NULL COMMENT '文件MIME类型' AFTER `file_type`,
    ADD COLUMN `retry_count` int NOT NULL DEFAULT 0 COMMENT '解析重试次数' AFTER `error_message`,
    ADD COLUMN `last_retry_time` datetime DEFAULT NULL COMMENT '最近一次重试时间' AFTER `retry_count`;

CREATE INDEX `idx_document_space_status_deleted`
    ON `document` (`space_id`, `status`, `is_deleted`);

CREATE INDEX `idx_document_folder_status_deleted`
    ON `document` (`folder_id`, `status`, `is_deleted`);

-- =========================================
-- P2: 预留表，按需要再执行
-- =========================================

-- 6. 通知中心（可选）
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `type` varchar(50) NOT NULL COMMENT '通知类型',
  `title` varchar(200) NOT NULL COMMENT '通知标题',
  `content` varchar(1000) DEFAULT NULL COMMENT '通知内容',
  `related_type` varchar(50) DEFAULT NULL COMMENT '关联资源类型',
  `related_id` bigint DEFAULT NULL COMMENT '关联资源ID',
  `is_read` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已读：0-否，1-是',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_notification_user_read_create` (`user_id`, `is_read`, `create_time`),
  CONSTRAINT `fk_notification_user`
      FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知表';

-- 7. 审计日志（可选）
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `username` varchar(50) DEFAULT NULL COMMENT '操作人用户名',
  `action` varchar(100) NOT NULL COMMENT '操作类型',
  `resource_type` varchar(50) DEFAULT NULL COMMENT '资源类型',
  `resource_id` bigint DEFAULT NULL COMMENT '资源ID',
  `space_id` bigint DEFAULT NULL COMMENT '关联空间ID',
  `detail` varchar(2000) DEFAULT NULL COMMENT '操作详情',
  `result` varchar(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果：SUCCESS / FAIL',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_log_user_id` (`user_id`),
  KEY `idx_audit_log_action` (`action`),
  KEY `idx_audit_log_resource` (`resource_type`, `resource_id`),
  KEY `idx_audit_log_space_id` (`space_id`),
  KEY `idx_audit_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志表';
