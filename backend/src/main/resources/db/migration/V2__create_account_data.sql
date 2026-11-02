CREATE TABLE student_profiles (
    user_id BIGINT NOT NULL PRIMARY KEY,
    profile_json TEXT NOT NULL,
    local_imported BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE match_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    skills_snapshot TEXT NOT NULL,
    interests_snapshot TEXT NOT NULL,
    results_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_match_sessions_owner_date ON match_sessions(user_id, created_at);

CREATE TABLE match_results (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    result_rank INT NOT NULL,
    onet_soc_code VARCHAR(15) NOT NULL,
    result_json TEXT NOT NULL,
    CONSTRAINT uq_session_rank UNIQUE (session_id, result_rank),
    CONSTRAINT fk_result_session FOREIGN KEY (session_id) REFERENCES match_sessions(id) ON DELETE CASCADE
);

CREATE TABLE saved_careers (
    user_id BIGINT NOT NULL,
    onet_soc_code VARCHAR(15) NOT NULL,
    career_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, onet_soc_code),
    CONSTRAINT fk_saved_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE roadmap_items (
    user_id BIGINT NOT NULL,
    item_key VARCHAR(120) NOT NULL,
    item_json TEXT NOT NULL,
    is_complete BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, item_key),
    CONSTRAINT fk_roadmap_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
