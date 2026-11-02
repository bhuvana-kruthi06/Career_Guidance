ALTER TABLE student_profiles
    ADD COLUMN target_role_code VARCHAR(15) NULL,
    ADD COLUMN target_role_title VARCHAR(160) NULL,
    ADD COLUMN profile_completeness TINYINT NOT NULL DEFAULT 0;

CREATE TABLE career_assessments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    assessment_json TEXT NOT NULL,
    recommendations_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assessment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_career_assessments_owner_date ON career_assessments(user_id, created_at);
