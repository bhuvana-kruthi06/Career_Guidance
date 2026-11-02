CREATE TABLE career_tool_records (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    record_type VARCHAR(32) NOT NULL,
    record_key VARCHAR(80) NOT NULL,
    status VARCHAR(24) NULL,
    record_json MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_career_tool_owner_type_key UNIQUE (user_id, record_type, record_key),
    CONSTRAINT chk_career_tool_type CHECK (record_type IN ('ROADMAP','PROJECT','MENTOR','INTERVIEW','JOB_DESCRIPTION')),
    CONSTRAINT fk_career_tool_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_career_tool_owner_type_updated ON career_tool_records(user_id, record_type, updated_at);

CREATE TABLE job_applications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company VARCHAR(160) NOT NULL,
    role_title VARCHAR(180) NOT NULL,
    job_link VARCHAR(1200) NULL,
    salary VARCHAR(120) NULL,
    location VARCHAR(160) NULL,
    resume_version VARCHAR(160) NULL,
    application_date DATE NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'Saved',
    notes VARCHAR(4000) NULL,
    follow_up_date DATE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_job_application_status CHECK (status IN ('Saved','Applied','Interview Scheduled','Offer','Rejected')),
    CONSTRAINT fk_job_application_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_job_application_owner_status ON job_applications(user_id, status, updated_at);
CREATE INDEX idx_job_application_owner_followup ON job_applications(user_id, follow_up_date);
