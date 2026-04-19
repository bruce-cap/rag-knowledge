-- Phase 3: remove document-level public flag and migrate to public knowledge space.

ALTER TABLE knowledge_space
    ADD COLUMN IF NOT EXISTS code VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS type VARCHAR(30) NOT NULL DEFAULT 'BUSINESS',
    ADD COLUMN IF NOT EXISTS is_system TINYINT(1) NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_space_code ON knowledge_space(code);

INSERT INTO knowledge_space (name, code, type, is_system, description, status, create_by, create_time, update_time)
SELECT '公共知识库', 'PUBLIC', 'SYSTEM_PUBLIC', 1, 'System-managed public knowledge space', 1, 0, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_space WHERE code = 'PUBLIC'
);

SET @public_space_id = (
    SELECT id FROM knowledge_space WHERE code = 'PUBLIC' LIMIT 1
);

UPDATE document
SET space_id = @public_space_id
WHERE space_id IS NULL OR is_public = 1;

INSERT INTO space_member (space_id, user_id, role, join_time)
SELECT @public_space_id, u.id, 'MEMBER', NOW()
FROM sys_user u
WHERE NOT EXISTS (
    SELECT 1
    FROM space_member sm
    WHERE sm.space_id = @public_space_id
      AND sm.user_id = u.id
);

ALTER TABLE document
    MODIFY COLUMN space_id BIGINT NOT NULL;

ALTER TABLE document
    DROP COLUMN is_public;
