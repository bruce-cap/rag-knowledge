-- MySQL dump 10.13  Distrib 8.0.43, for Win64 (x86_64)
--
-- Host: localhost    Database: rag
-- ------------------------------------------------------
-- Server version	8.0.43

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `rag`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `rag` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `rag`;

--
-- Table structure for table `chat_message`
--

DROP TABLE IF EXISTS `chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL COMMENT '所属会话ID',
  `role` varchar(20) NOT NULL COMMENT '角色：user 或 assistant',
  `content` text NOT NULL COMMENT '对话内容',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_chat_message_session_create_time` (`session_id`,`create_time`),
  CONSTRAINT `fk_chat_message_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=992 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `chat_session`
--

DROP TABLE IF EXISTS `chat_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `title` varchar(255) DEFAULT '新的会话' COMMENT '会话标题，可由AI生成',
  `is_title_manual` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已手动修改标题：0-否，1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否置顶：0-否，1-是',
  `pin_time` datetime DEFAULT NULL COMMENT '置顶时间',
  PRIMARY KEY (`id`),
  KEY `idx_chat_session_user_update` (`user_id`,`is_deleted`,`update_time`),
  KEY `idx_chat_session_user_pinned_update` (`user_id`,`is_deleted`,`is_pinned`,`pin_time`,`update_time`),
  CONSTRAINT `fk_chat_session_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `document`
--

DROP TABLE IF EXISTS `document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `space_id` bigint NOT NULL COMMENT '所属知识空间ID',
  `folder_id` bigint DEFAULT NULL COMMENT '所属文件夹ID',
  `user_id` bigint NOT NULL COMMENT '上传者ID',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `minio_path` varchar(512) NOT NULL COMMENT 'MinIO对象路径',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `file_type` varchar(50) DEFAULT NULL COMMENT '文件类型',
  `mime_type` varchar(100) DEFAULT NULL COMMENT '文件MIME类型',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-待处理，1-处理中，2-处理完成，3-处理失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除：0-否，1-是',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '处理失败原因',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '解析重试次数',
  `last_retry_time` datetime DEFAULT NULL COMMENT '最近一次重试时间',
  PRIMARY KEY (`id`),
  KEY `idx_document_space_id` (`space_id`),
  KEY `idx_document_folder_id` (`folder_id`),
  KEY `idx_document_user_id` (`user_id`),
  KEY `idx_document_status` (`status`),
  KEY `idx_document_is_deleted` (`is_deleted`),
  KEY `idx_document_space_status_deleted` (`space_id`,`status`,`is_deleted`),
  KEY `idx_document_folder_status_deleted` (`folder_id`,`status`,`is_deleted`),
  CONSTRAINT `fk_document_folder` FOREIGN KEY (`folder_id`) REFERENCES `folder` (`id`),
  CONSTRAINT `fk_document_space` FOREIGN KEY (`space_id`) REFERENCES `knowledge_space` (`id`),
  CONSTRAINT `fk_document_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `folder`
--

DROP TABLE IF EXISTS `folder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `folder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `space_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_folder_space` (`space_id`),
  KEY `fk_folder_parent` (`parent_id`),
  KEY `fk_folder_creator` (`create_by`),
  CONSTRAINT `fk_folder_creator` FOREIGN KEY (`create_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_folder_parent` FOREIGN KEY (`parent_id`) REFERENCES `folder` (`id`),
  CONSTRAINT `fk_folder_space` FOREIGN KEY (`space_id`) REFERENCES `knowledge_space` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_space`
--

DROP TABLE IF EXISTS `knowledge_space`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_space` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `create_by` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `code` varchar(50) DEFAULT NULL,
  `type` varchar(30) NOT NULL DEFAULT 'BUSINESS',
  `is_system` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_space_create_by` (`create_by`),
  UNIQUE KEY `uk_knowledge_space_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `space_join_request`
--

DROP TABLE IF EXISTS `space_join_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `space_join_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `space_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
    `target_role` varchar(20) NOT NULL DEFAULT 'VIEW' COMMENT '申请加入后的目标角色：VIEW / MEMBER / ADMIN',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `apply_reason` varchar(500) DEFAULT NULL,
  `review_reason` varchar(500) DEFAULT NULL,
  `review_by` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_space_join_request_space_id` (`space_id`),
  KEY `idx_space_join_request_user_id` (`user_id`),
  KEY `idx_space_join_request_status` (`status`),
  KEY `fk_space_join_request_review_user` (`review_by`),
  CONSTRAINT `fk_space_join_request_review_user` FOREIGN KEY (`review_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_space_join_request_space` FOREIGN KEY (`space_id`) REFERENCES `knowledge_space` (`id`),
  CONSTRAINT `fk_space_join_request_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `space_member`
--

DROP TABLE IF EXISTS `space_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `space_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `space_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role` varchar(20) NOT NULL DEFAULT 'VIEW' COMMENT '空间角色：ADMIN / MEMBER / VIEW',
  `join_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_space_member` (`space_id`,`user_id`),
  KEY `fk_space_member_user` (`user_id`),
  CONSTRAINT `fk_space_member_space` FOREIGN KEY (`space_id`) REFERENCES `knowledge_space` (`id`),
  CONSTRAINT `fk_space_member_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `space_role_request`
--

DROP TABLE IF EXISTS `space_role_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  KEY `idx_space_role_request_space_user_status` (`space_id`,`user_id`,`status`),
  KEY `fk_space_role_request_review_user` (`review_by`),
  CONSTRAINT `fk_space_role_request_review_user` FOREIGN KEY (`review_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_space_role_request_space` FOREIGN KEY (`space_id`) REFERENCES `knowledge_space` (`id`),
  CONSTRAINT `fk_space_role_request_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_space_role_request_current_role` CHECK ((`current_role` in (_utf8mb4'ADMIN',_utf8mb4'MEMBER',_utf8mb4'VIEW'))),
  CONSTRAINT `chk_space_role_request_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'APPROVED',_utf8mb4'REJECTED',_utf8mb4'CANCELED'))),
  CONSTRAINT `chk_space_role_request_target_role` CHECK ((`target_role` in (_utf8mb4'ADMIN',_utf8mb4'MEMBER',_utf8mb4'VIEW'))),
  CONSTRAINT `chk_space_role_request_type` CHECK ((`request_type` in (_utf8mb4'UPLOAD_PERMISSION',_utf8mb4'ROLE_UPGRADE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='空间角色申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码(加密存储)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `email` varchar(100) DEFAULT NULL COMMENT '用户邮箱',
  `nickname` varchar(100) DEFAULT NULL COMMENT '用户昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `role` varchar(50) DEFAULT 'USER',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '用户状态：1-启用，0-禁用',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `uk_sys_user_email` (`email`),
  KEY `idx_sys_user_status` (`status`),
  KEY `idx_sys_user_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

ALTER TABLE `knowledge_space`
  ADD CONSTRAINT `fk_knowledge_space_creator` FOREIGN KEY (`create_by`) REFERENCES `sys_user` (`id`);

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-20 12:57:06

