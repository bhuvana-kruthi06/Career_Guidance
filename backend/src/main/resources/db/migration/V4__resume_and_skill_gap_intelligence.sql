CREATE TABLE resume_analyses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_role_code VARCHAR(15) NULL,
    extracted_json MEDIUMTEXT NOT NULL,
    report_json MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_resume_owner_date ON resume_analyses(user_id, created_at);

CREATE TABLE skill_gap_progress (
    user_id BIGINT NOT NULL,
    role_code VARCHAR(15) NOT NULL,
    skill_name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_code, skill_name),
    CONSTRAINT chk_skill_gap_status CHECK (status IN ('Learning', 'Learned')),
    CONSTRAINT fk_skill_gap_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
