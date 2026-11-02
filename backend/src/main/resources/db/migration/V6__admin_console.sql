ALTER TABLE users
    ADD COLUMN full_name VARCHAR(80) NULL,
    ADD COLUMN account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN last_active_at TIMESTAMP NULL,
    ADD CONSTRAINT chk_users_status CHECK (account_status IN ('ACTIVE','SUSPENDED','ARCHIVED'));

UPDATE users SET account_status = CASE WHEN is_active THEN 'ACTIVE' ELSE 'SUSPENDED' END;

CREATE TABLE admin_user_notes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    note VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_admin_note_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_note_author FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_admin_note_user_date ON admin_user_notes(user_id, created_at);

CREATE TABLE admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    action VARCHAR(48) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(80) NOT NULL,
    details_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_admin_audit_admin FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_admin_audit_date ON admin_audit_logs(created_at);

CREATE TABLE admin_content_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content_json MEDIUMTEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    archived_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_admin_content_type CHECK (content_type IN ('CAREER_ROLE','SKILL','ASSESSMENT_QUESTION','ROADMAP_TEMPLATE','PROJECT','LEARNING_RESOURCE'))
);
CREATE INDEX idx_admin_content_type_enabled ON admin_content_items(content_type, enabled, archived_at);
