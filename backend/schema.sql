-- ============================================================
-- AI-Powered Personalised Career Guidance and Skill Gap Analysis Platform
-- Schema Version : 2.0
-- Database       : MySQL 8.0
-- Created        : 2026-10-26
-- Author         : Backend Database Engineer
--
-- Description:
--   Full DDL for the AI Career Guidance System.
--   Tables are created in strict dependency order — no FK
--   ever references a table that has not yet been defined.
--   26 tables across 10 groups (including two mock-interview tables).
--
-- Load order after applying this file:
--   1. This script creates the database, all tables, and seeds
--      onet_occupations + onet_skill_elements inline.
--   2. Run backend/load_onet_data.py to load onet_career_skill_map
--      (50,261 rows — too large to inline).
--   3. Optionally seed riasec_questions and learning_resources
--      from the admin panel or a separate seed script.
--
-- Groups:
--   1. Auth & Users          (users, user_tokens)
--   2. Student Profile       (student_profiles, riasec_questions, riasec_answers)
--   3. Skill Assessment      (onet_skill_elements, student_skills, skill_evidence)
--   4. Career Matching       (onet_occupations, career_match_sessions,
--                             career_match_results, match_score_history,
--                             whatif_scenarios)
--   5. Skill Gap Analysis    (skill_gap_results)
--   6. AI Guidance           (ai_conversations, ai_messages, ai_feedback)
--   7. Learning Roadmap      (learning_roadmaps, learning_resources,
--                             roadmap_items, roadmap_milestones)
--   8. Progress Tracking     (skill_progress_history)
--   9. Admin / O*NET Data    (onet_career_skill_map, career_transitions)
--  10. Mock Interviews       (mock_interview_questions, student_interview_attempts)
-- ============================================================

-- ============================================================
-- DATABASE
-- ============================================================
CREATE DATABASE IF NOT EXISTS ai_career_guidance
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ai_career_guidance;

-- ============================================================
-- GROUP 1: Authentication & Users
-- ============================================================

-- ------------------------------------------------------------
-- users
-- Stores login credentials. Passwords are always BCrypt hashed.
-- Role ENUM enforces access control (Spring Security @PreAuthorize).
-- No separate roles table needed for two-role system.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL COMMENT 'BCrypt hash — never plaintext',
    role            ENUM('STUDENT','ADMIN') NOT NULL DEFAULT 'STUDENT',
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE  COMMENT 'Soft-disable without deleting',
    email_verified  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
    last_login_at   DATETIME                 DEFAULT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    CONSTRAINT chk_users_role CHECK (role IN ('STUDENT','ADMIN'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Login credentials. BCrypt passwords. Two-role system via ENUM.';

-- ------------------------------------------------------------
-- user_tokens
-- Short-lived tokens for email verification, password reset,
-- and JWT refresh. Token hash (SHA-256) stored — never raw token.
-- One-time use enforced via used_at column.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    token_hash  VARCHAR(255) NOT NULL COMMENT 'SHA-256 of the actual token',
    token_type  ENUM('EMAIL_VERIFY','PASSWORD_RESET','REFRESH') NOT NULL,
    expires_at  DATETIME    NOT NULL,
    used_at     DATETIME             DEFAULT NULL COMMENT 'NULL = not yet used',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_user_tokens_hash (token_hash),
    INDEX idx_user_tokens_user   (user_id, token_type),
    INDEX idx_user_tokens_expiry (expires_at),
    CONSTRAINT fk_utok_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Email-verify, password-reset, and JWT refresh tokens. Hash stored, not plaintext.';

-- ============================================================
-- GROUP 2: Student Profile & RIASEC
-- ============================================================

-- ------------------------------------------------------------
-- student_profiles
-- One-to-one with users. Stores personal and academic context.
-- RIASEC interest columns here are derived from riasec_answers
-- (or set directly if student skips the quiz).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_profiles (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT          NOT NULL,
    full_name               VARCHAR(255)    NOT NULL,
    current_education_level ENUM('High School','Diploma','Bachelor',
                                 'Master','PhD','Other') DEFAULT NULL,
    field_of_study          VARCHAR(255)    DEFAULT NULL COMMENT 'e.g. Computer Science',
    institution             VARCHAR(255)    DEFAULT NULL,
    graduation_year         YEAR            DEFAULT NULL,
    riasec_interest_1       CHAR(1)         DEFAULT NULL COMMENT 'Top Holland code: R/I/A/S/E/C',
    riasec_interest_2       CHAR(1)         DEFAULT NULL,
    riasec_interest_3       CHAR(1)         DEFAULT NULL,
    preferred_job_zone_min  TINYINT         DEFAULT NULL COMMENT 'Min prep level 1-5',
    preferred_job_zone_max  TINYINT         DEFAULT NULL COMMENT 'Max prep level 1-5',
    profile_completed       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_sp_user (user_id),
    CONSTRAINT fk_sp_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_sp_riasec_1 CHECK (
        riasec_interest_1 IN ('R','I','A','S','E','C') OR riasec_interest_1 IS NULL),
    CONSTRAINT chk_sp_riasec_2 CHECK (
        riasec_interest_2 IN ('R','I','A','S','E','C') OR riasec_interest_2 IS NULL),
    CONSTRAINT chk_sp_riasec_3 CHECK (
        riasec_interest_3 IN ('R','I','A','S','E','C') OR riasec_interest_3 IS NULL),
    CONSTRAINT chk_sp_zone_min CHECK (
        preferred_job_zone_min BETWEEN 1 AND 5 OR preferred_job_zone_min IS NULL),
    CONSTRAINT chk_sp_zone_max CHECK (
        preferred_job_zone_max BETWEEN 1 AND 5 OR preferred_job_zone_max IS NULL)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='One-to-one with users. Academic context and derived RIASEC interest codes.';

-- ------------------------------------------------------------
-- riasec_questions
-- Fixed question bank seeded by admin. Students answer these
-- to compute their Holland (RIASEC) interest codes.
-- Each question is tagged to one dimension (R/I/A/S/E/C).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS riasec_questions (
    id                  INT         NOT NULL AUTO_INCREMENT,
    question_text       TEXT        NOT NULL,
    riasec_dimension    CHAR(1)     NOT NULL COMMENT 'R/I/A/S/E/C — which dimension this scores',
    display_order       SMALLINT    NOT NULL,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE COMMENT 'Admin can retire questions',

    PRIMARY KEY (id),
    CONSTRAINT chk_rq_dimension CHECK (riasec_dimension IN ('R','I','A','S','E','C'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='RIASEC question bank. Seeded by admin. Answers stored in riasec_answers.';

-- ------------------------------------------------------------
-- riasec_answers
-- One row per student per question. Makes RIASEC codes auditable
-- and re-computable if the scoring algorithm changes.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS riasec_answers (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    question_id INT         NOT NULL,
    answer_score TINYINT    NOT NULL COMMENT '1=Strongly Disagree, 5=Strongly Agree',
    answered_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_ra_user_question (user_id, question_id),
    CONSTRAINT fk_ra_user     FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ra_question FOREIGN KEY (question_id)
        REFERENCES riasec_questions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_ra_score CHECK (answer_score BETWEEN 1 AND 5)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Student RIASEC quiz answers. Auditable — codes can be recomputed from raw answers.';

-- ============================================================
-- GROUP 3a: O*NET Master Skill Elements
-- (Created before student_skills — it is referenced as FK target)
-- ============================================================

-- ------------------------------------------------------------
-- onet_skill_elements
-- Master list of the 68 skill elements used in career matching.
-- Seeded from O*NET 31.0. Read-only in production.
-- element_id format: "2.A.1.a" (O*NET Content Model IDs)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS onet_skill_elements (
    element_id      VARCHAR(20)     NOT NULL COMMENT 'O*NET Content Model ID e.g. 2.A.1.a',
    element_name    VARCHAR(255)    NOT NULL,
    skill_category  ENUM('Essential','Transferable','Knowledge','Ability') NOT NULL,
    description     TEXT            DEFAULT NULL,
    dataset_version VARCHAR(20)     NOT NULL DEFAULT '31.0',

    PRIMARY KEY (element_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='68 O*NET skill elements used in matching. Read-only reference data.';

-- ============================================================
-- GROUP 3b: Skill Assessment
-- ============================================================

-- ------------------------------------------------------------
-- student_skills
-- Self-rated skills on a 1-5 scale. One row per student per skill.
-- confidence_weight (0.50-1.00) is boosted by verified skill_evidence rows.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_skills (
    id                BIGINT          NOT NULL AUTO_INCREMENT,
    user_id           BIGINT          NOT NULL,
    element_id        VARCHAR(20)     NOT NULL,
    rating            TINYINT         NOT NULL COMMENT '1=Beginner, 5=Expert',
    confidence_weight DECIMAL(3,2)    NOT NULL DEFAULT 1.00
                          COMMENT '0.50=self-report only; up to 1.00 with verified evidence',
    is_self_reported  BOOLEAN         NOT NULL DEFAULT TRUE
                          COMMENT 'FALSE when at least one evidence item is verified',
    rated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                          ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_ss_user_element (user_id, element_id),
    INDEX idx_student_skills_user (user_id),
    CONSTRAINT fk_ss_user    FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ss_element FOREIGN KEY (element_id)
        REFERENCES onet_skill_elements (element_id) ON DELETE RESTRICT,
    CONSTRAINT chk_ss_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_ss_conf   CHECK (confidence_weight BETWEEN 0.50 AND 1.00)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Student self-rated skills (1-5). confidence_weight increases with verified evidence.';

-- ------------------------------------------------------------
-- skill_evidence  [Differentiator 1]
-- Certificate / project / GitHub / course evidence backing a skill.
-- confidence_contribution (default 0.10) is added to student_skills.confidence_weight
-- when is_verified = TRUE, capped at 1.00.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS skill_evidence (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    user_id                 BIGINT          NOT NULL,
    element_id              VARCHAR(20)     NOT NULL,
    evidence_type           ENUM('Certificate','Project','GitHub','Course','Other') NOT NULL,
    title                   VARCHAR(500)    NOT NULL COMMENT 'e.g. AWS Certified Solutions Architect',
    url                     VARCHAR(2048)   DEFAULT NULL,
    issuer                  VARCHAR(255)    DEFAULT NULL COMMENT 'e.g. Coursera, GitHub',
    issued_date             DATE            DEFAULT NULL,
    is_verified             BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Admin/system verified',
    confidence_contribution DECIMAL(3,2)    NOT NULL DEFAULT 0.10
                                COMMENT 'Added to student_skills.confidence_weight when verified',
    submitted_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_se_user    FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_se_element FOREIGN KEY (element_id)
        REFERENCES onet_skill_elements (element_id) ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Evidence backing skill claims (certs, projects, GitHub). Boosts confidence_weight when verified.';

-- ============================================================
-- GROUP 4a: O*NET Occupations Master
-- (Created before career_match_results — it is referenced as FK target)
-- ============================================================

-- ------------------------------------------------------------
-- onet_occupations
-- Master occupation list from O*NET 31.0. Read-only in production.
-- 910 occupations with skill data; 1,016 total.
-- Seeded inline below by INSERT statements generated from career_metadata.csv.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS onet_occupations (
    onet_soc_code       VARCHAR(15)     NOT NULL COMMENT 'e.g. 15-1252.00',
    occupation_title    VARCHAR(255)    NOT NULL,
    description         TEXT            DEFAULT NULL,
    job_zone            TINYINT         DEFAULT NULL COMMENT '1-5 preparation level',
    riasec_top1         CHAR(1)         DEFAULT NULL,
    riasec_top2         CHAR(1)         DEFAULT NULL,
    riasec_top3         CHAR(1)         DEFAULT NULL,
    dataset_version     VARCHAR(20)     NOT NULL DEFAULT '31.0',
    loaded_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (onet_soc_code),
    CONSTRAINT chk_oo_job_zone CHECK (job_zone BETWEEN 1 AND 5 OR job_zone IS NULL)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='O*NET 31.0 occupation master list. 910 occupations with skill data. Read-only.';

-- ============================================================
-- GROUP 4b: Career Matching
-- ============================================================

-- ------------------------------------------------------------
-- career_match_sessions
-- One session = one run of the matching engine by a student.
-- skills_snapshot stores the exact ratings used — immutable audit trail.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS career_match_sessions (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          NOT NULL,
    session_token       VARCHAR(64)     NOT NULL COMMENT 'UUID identifying this run',
    skills_snapshot     JSON            NOT NULL COMMENT 'Skill ratings at match time — audit trail',
    interests_snapshot  JSON            DEFAULT NULL COMMENT 'RIASEC codes used in this session',
    min_score_threshold DECIMAL(5,2)    NOT NULL DEFAULT 20.00,
    top_n_requested     TINYINT         NOT NULL DEFAULT 10,
    engine_version      VARCHAR(20)     NOT NULL DEFAULT '1.0',
    run_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_cms_token (session_token),
    CONSTRAINT fk_cms_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='One row per matching engine run. skills_snapshot is an immutable audit record.';

-- ------------------------------------------------------------
-- career_match_results
-- Top-N career results for a session. One row per career per session.
-- readiness_score is confidence-weighted match_score (Differentiator 3).
-- base_score + riasec_bonus stored separately for explainability.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS career_match_results (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    session_id          BIGINT          NOT NULL,
    `rank`              TINYINT         NOT NULL COMMENT '1 = best match',
    onet_soc_code       VARCHAR(15)     NOT NULL,
    occupation_title    VARCHAR(255)    NOT NULL COMMENT 'Denormalized for fast display',
    match_score         DECIMAL(5,2)    NOT NULL COMMENT '0-100 weighted skill overlap score',
    base_score          DECIMAL(5,2)    NOT NULL COMMENT 'Score before RIASEC bonus',
    riasec_bonus        DECIMAL(4,2)    NOT NULL DEFAULT 0.00,
    readiness_score     DECIMAL(5,2)    DEFAULT NULL
                            COMMENT 'Confidence-weighted match score — Differentiator 3',
    skills_assessed     SMALLINT        NOT NULL COMMENT 'How many career skills student rated',
    skills_total        SMALLINT        NOT NULL COMMENT 'Total skills in career map',
    strong_skills       JSON            DEFAULT NULL COMMENT 'Skill names where student met requirement',
    is_saved            BOOLEAN         NOT NULL DEFAULT FALSE COMMENT 'Student bookmarked',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_cmr_session_rank (session_id, `rank`),
    CONSTRAINT fk_cmr_session  FOREIGN KEY (session_id)
        REFERENCES career_match_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_cmr_occ      FOREIGN KEY (onet_soc_code)
        REFERENCES onet_occupations (onet_soc_code) ON DELETE RESTRICT,
    CONSTRAINT chk_cmr_match   CHECK (match_score    BETWEEN 0.00 AND 100.00),
    CONSTRAINT chk_cmr_base    CHECK (base_score     BETWEEN 0.00 AND 100.00),
    CONSTRAINT chk_cmr_ready   CHECK (readiness_score BETWEEN 0.00 AND 100.00
                                      OR readiness_score IS NULL)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Top-N career matches per session. Stores base + bonus scores separately for explainability.';

-- ------------------------------------------------------------
-- match_score_history  [Differentiator 2]
-- Snapshots match_score every time a student runs matching for
-- the same career. Enables a line chart of score improvement over time.
-- Written by CareerMatchService at end of every session.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS match_score_history (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    onet_soc_code   VARCHAR(15)     NOT NULL,
    match_score     DECIMAL(5,2)    NOT NULL,
    readiness_score DECIMAL(5,2)    DEFAULT NULL,
    session_id      BIGINT          NOT NULL,
    recorded_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_msh_user    FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_msh_occ     FOREIGN KEY (onet_soc_code)
        REFERENCES onet_occupations (onet_soc_code) ON DELETE RESTRICT,
    CONSTRAINT fk_msh_session FOREIGN KEY (session_id)
        REFERENCES career_match_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Career match score over time per student. Powers progress line chart.';

-- ------------------------------------------------------------
-- whatif_scenarios  [Differentiator 6]
-- Hypothetical skill changes + projected match scores.
-- Does NOT modify real student_skills — simulation only.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS whatif_scenarios (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          NOT NULL,
    session_id          BIGINT          NOT NULL COMMENT 'Base session to compare against',
    scenario_name       VARCHAR(255)    NOT NULL COMMENT 'Student-given label',
    hypothetical_skills JSON            NOT NULL
                            COMMENT '{element_id: hypothetical_rating, ...}',
    projected_scores    JSON            DEFAULT NULL
                            COMMENT '{onet_soc_code: projected_score, ...} — computed on save',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_ws_user    FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ws_session FOREIGN KEY (session_id)
        REFERENCES career_match_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='What-if skill simulations. Hypothetical ratings projected to match scores. No real data modified.';

-- ============================================================
-- GROUP 5: Skill Gap Analysis
-- ============================================================

-- ------------------------------------------------------------
-- skill_gap_results
-- One row per skill gap per career per session.
-- gap_priority = gap_size × importance_score (drives roadmap ordering).
-- priority_label: Critical / Moderate / Minor.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS skill_gap_results (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    career_result_id    BIGINT          NOT NULL,
    element_id          VARCHAR(20)     NOT NULL,
    element_name        VARCHAR(255)    NOT NULL COMMENT 'Denormalized for display',
    skill_category      ENUM('Essential','Transferable','Knowledge','Ability') NOT NULL,
    student_level       DECIMAL(4,2)    NOT NULL COMMENT 'Mapped O*NET 0-7 scale',
    required_level      DECIMAL(4,2)    NOT NULL COMMENT 'O*NET Level score',
    student_rating      TINYINT         NOT NULL COMMENT 'Original 1-5 input',
    importance_score    DECIMAL(4,2)    NOT NULL COMMENT 'O*NET Importance 1-5',
    gap_size            DECIMAL(4,2)    NOT NULL COMMENT 'required_level - student_level',
    gap_priority        DECIMAL(6,2)    NOT NULL COMMENT 'gap_size × importance_score',
    priority_label      ENUM('Critical','Moderate','Minor') NOT NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_sgr_career_result (career_result_id),
    CONSTRAINT fk_sgr_career  FOREIGN KEY (career_result_id)
        REFERENCES career_match_results (id) ON DELETE CASCADE,
    CONSTRAINT fk_sgr_element FOREIGN KEY (element_id)
        REFERENCES onet_skill_elements (element_id) ON DELETE RESTRICT,
    CONSTRAINT chk_sgr_gap_size CHECK (gap_size >= 0.00)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Skill gaps per career per session. gap_priority drives roadmap item ordering.';

-- ============================================================
-- GROUP 6: AI Career Guidance (Phase-III stub — tables empty until Phase-III)
-- ============================================================

-- ------------------------------------------------------------
-- ai_conversations
-- One conversation = one AI chat session linked to a career gap analysis.
-- Defined now; activated in Phase-III when LLM API is integrated.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_conversations (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          NOT NULL,
    session_id          BIGINT          DEFAULT NULL
                            COMMENT 'Match session that grounds this conversation',
    career_result_id    BIGINT          DEFAULT NULL
                            COMMENT 'Which career the AI discusses',
    title               VARCHAR(500)    DEFAULT NULL
                            COMMENT 'Auto-generated from first message',
    llm_provider        VARCHAR(50)     DEFAULT NULL COMMENT 'e.g. gemini, openai',
    model_version       VARCHAR(50)     DEFAULT NULL COMMENT 'e.g. gemini-1.5-flash',
    started_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at            DATETIME        DEFAULT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_ac_user   FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ac_session FOREIGN KEY (session_id)
        REFERENCES career_match_sessions (id) ON DELETE SET NULL,
    CONSTRAINT fk_ac_career  FOREIGN KEY (career_result_id)
        REFERENCES career_match_results (id) ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='AI chat sessions. Linked to match session for grounded guidance. Phase-III.';

-- ------------------------------------------------------------
-- ai_messages
-- Individual messages in a conversation.
-- grounding_gap_ids stores the skill_gap_results.id values fed as LLM context.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_messages (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    conversation_id     BIGINT          NOT NULL,
    role                ENUM('USER','ASSISTANT') NOT NULL,
    content             TEXT            NOT NULL,
    grounding_gap_ids   JSON            DEFAULT NULL
                            COMMENT 'Array of skill_gap_results.id fed as LLM grounding context',
    tokens_used         INT             DEFAULT NULL COMMENT 'LLM token cost tracking',
    sent_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_am_conv FOREIGN KEY (conversation_id)
        REFERENCES ai_conversations (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='AI conversation messages. grounding_gap_ids links each AI reply to the gap data it used.';

-- ------------------------------------------------------------
-- ai_feedback
-- Student thumbs-up/down rating on each AI response.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_feedback (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    message_id      BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    rating          TINYINT         NOT NULL COMMENT '1=thumbs down, 2=thumbs up',
    comment         VARCHAR(1000)   DEFAULT NULL,
    submitted_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_af_message FOREIGN KEY (message_id)
        REFERENCES ai_messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_af_user    FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_af_rating CHECK (rating IN (1, 2))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Student feedback (thumbs up/down) on AI responses. Phase-III.';

-- ============================================================
-- GROUP 7: Learning Roadmap
-- ============================================================

-- ------------------------------------------------------------
-- learning_roadmaps
-- One roadmap per student per target career.
-- onet_soc_code is now a real FK (fixed from v1).
-- target_date + weekly_hours enable adaptive milestone planning.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS learning_roadmaps (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          NOT NULL,
    career_result_id    BIGINT          NOT NULL,
    onet_soc_code       VARCHAR(15)     NOT NULL COMMENT 'FK — now a real FK (fixed from v1)',
    occupation_title    VARCHAR(255)    NOT NULL,
    total_gaps          SMALLINT        NOT NULL DEFAULT 0,
    critical_gaps       SMALLINT        NOT NULL DEFAULT 0,
    target_date         DATE            DEFAULT NULL
                            COMMENT 'When student wants to be career-ready',
    weekly_hours        TINYINT         DEFAULT NULL
                            COMMENT 'Study hours per week — used for milestone scheduling',
    status              ENUM('Active','Completed','Abandoned') NOT NULL DEFAULT 'Active',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_lr_user    FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_lr_career  FOREIGN KEY (career_result_id)
        REFERENCES career_match_results (id) ON DELETE CASCADE,
    CONSTRAINT fk_lr_occ     FOREIGN KEY (onet_soc_code)
        REFERENCES onet_occupations (onet_soc_code) ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='One roadmap per student per career. target_date and weekly_hours enable adaptive milestones.';

-- ------------------------------------------------------------
-- learning_resources  [Differentiator 4 — Catalog]
-- Reusable course/resource catalog seeded by admin.
-- Multiple students roadmaps reference the same resource row.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS learning_resources (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    element_id      VARCHAR(20)     NOT NULL COMMENT 'Which O*NET skill this resource teaches',
    title           VARCHAR(500)    NOT NULL,
    provider        VARCHAR(255)    DEFAULT NULL COMMENT 'e.g. Coursera, YouTube, edX',
    url             VARCHAR(2048)   DEFAULT NULL,
    resource_type   ENUM('Course','Book','Project','Video','Practice','Other') NOT NULL,
    cost            ENUM('Free','Paid','Freemium') NOT NULL DEFAULT 'Free',
    duration_hours  DECIMAL(6,1)    DEFAULT NULL COMMENT 'Estimated completion hours',
    level           ENUM('Beginner','Intermediate','Advanced') DEFAULT NULL,
    language        VARCHAR(10)     NOT NULL DEFAULT 'en' COMMENT 'ISO 639-1',
    region          VARCHAR(50)     DEFAULT 'Global',
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    added_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_lres_element FOREIGN KEY (element_id)
        REFERENCES onet_skill_elements (element_id) ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Admin-seeded resource catalog. Reused across multiple student roadmaps.';

-- ------------------------------------------------------------
-- roadmap_items  (restructured from v1 roadmap_resources)
-- Each row = one skill gap addressed by one learning resource in a roadmap.
-- resource_id is nullable — gap may exist before a resource is assigned.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roadmap_items (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    roadmap_id      BIGINT          NOT NULL,
    gap_result_id   BIGINT          NOT NULL,
    resource_id     BIGINT          DEFAULT NULL
                        COMMENT 'NULL if no resource assigned yet',
    element_id      VARCHAR(20)     NOT NULL COMMENT 'Denormalized for quick filter',
    priority_order  SMALLINT        NOT NULL COMMENT '1 = address first',
    status          ENUM('Not Started','In Progress','Completed','Skipped')
                        NOT NULL DEFAULT 'Not Started',
    started_at      DATETIME        DEFAULT NULL,
    completed_at    DATETIME        DEFAULT NULL,
    added_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_ri_roadmap  FOREIGN KEY (roadmap_id)
        REFERENCES learning_roadmaps (id) ON DELETE CASCADE,
    CONSTRAINT fk_ri_gap      FOREIGN KEY (gap_result_id)
        REFERENCES skill_gap_results (id) ON DELETE CASCADE,
    CONSTRAINT fk_ri_resource FOREIGN KEY (resource_id)
        REFERENCES learning_resources (id) ON DELETE SET NULL,
    CONSTRAINT fk_ri_element  FOREIGN KEY (element_id)
        REFERENCES onet_skill_elements (element_id) ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Roadmap items linking gaps to learning resources. Replaces v1 roadmap_resources.';

-- ------------------------------------------------------------
-- roadmap_milestones  [Differentiator 7 — Adaptive Roadmap]
-- Auto-generated checkpoints from target_date + weekly_hours.
-- replan_triggered = TRUE signals MilestoneService to push due dates.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roadmap_milestones (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    roadmap_id          BIGINT          NOT NULL,
    milestone_number    TINYINT         NOT NULL COMMENT '1, 2, 3 ...',
    title               VARCHAR(255)    NOT NULL COMMENT 'e.g. Complete Critical Skills',
    target_items_count  SMALLINT        NOT NULL COMMENT 'Roadmap items in this milestone',
    due_date            DATE            NOT NULL COMMENT 'Calculated from target_date / N milestones',
    status              ENUM('Pending','In Progress','Completed','Missed')
                            NOT NULL DEFAULT 'Pending',
    completed_at        DATE            DEFAULT NULL,
    replan_triggered    BOOLEAN         NOT NULL DEFAULT FALSE
                            COMMENT 'TRUE when missed milestone triggers auto-replan',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_rm_roadmap FOREIGN KEY (roadmap_id)
        REFERENCES learning_roadmaps (id) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Time-bound roadmap checkpoints. replan_triggered flag drives adaptive rescheduling.';

-- ============================================================
-- GROUP 8: Progress Tracking
-- ============================================================

-- ------------------------------------------------------------
-- skill_progress_history
-- One row per skill rating change. Tracks student growth over time.
-- old_rating = NULL on first entry (no previous rating).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS skill_progress_history (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    element_id  VARCHAR(20)     NOT NULL,
    old_rating  TINYINT         DEFAULT NULL COMMENT 'NULL if first ever rating',
    new_rating  TINYINT         NOT NULL,
    changed_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note        VARCHAR(500)    DEFAULT NULL COMMENT 'Optional student note on what they learned',

    PRIMARY KEY (id),
    CONSTRAINT fk_sph_user    FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_sph_element FOREIGN KEY (element_id)
        REFERENCES onet_skill_elements (element_id) ON DELETE RESTRICT,
    CONSTRAINT chk_sph_old CHECK (old_rating BETWEEN 1 AND 5 OR old_rating IS NULL),
    CONSTRAINT chk_sph_new CHECK (new_rating BETWEEN 1 AND 5)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Audit log of all skill rating changes. Powers student growth tracking.';

-- ============================================================
-- GROUP 9: Admin / O*NET Dataset Management
-- ============================================================

-- ------------------------------------------------------------
-- onet_career_skill_map
-- The matching engine lookup table. 50,261 rows.
-- DO NOT insert inline — use backend/load_onet_data.py instead.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS onet_career_skill_map (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    onet_soc_code   VARCHAR(15)     NOT NULL,
    element_id      VARCHAR(20)     NOT NULL,
    importance_score DECIMAL(4,2)   NOT NULL COMMENT 'O*NET IM scale 1.0-5.0',
    level_score     DECIMAL(4,2)    DEFAULT NULL COMMENT 'O*NET LV scale 0.0-7.0',
    dataset_version VARCHAR(20)     NOT NULL DEFAULT '31.0',

    PRIMARY KEY (id),
    UNIQUE KEY uq_ocsm_occ_elem (onet_soc_code, element_id),
    CONSTRAINT fk_ocsm_occ  FOREIGN KEY (onet_soc_code)
        REFERENCES onet_occupations (onet_soc_code) ON DELETE RESTRICT,
    CONSTRAINT fk_ocsm_elem FOREIGN KEY (element_id)
        REFERENCES onet_skill_elements (element_id) ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Matching engine lookup. 50,261 rows loaded by load_onet_data.py — not inlined here.';

-- ------------------------------------------------------------
-- career_transitions  [Differentiator 3 — Career Graph]
-- Precomputed pairwise skill overlap between careers.
-- Built offline by data_processing/build_career_transitions.py.
-- Only pairs with skill_overlap_pct >= 30% are stored (~50-100K rows).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS career_transitions (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    from_soc_code           VARCHAR(15)     NOT NULL COMMENT 'Current career',
    to_soc_code             VARCHAR(15)     NOT NULL COMMENT 'Target career',
    skill_overlap_pct       DECIMAL(5,2)    NOT NULL
                                COMMENT '% of target careers skills already in current career',
    shared_skills_count     SMALLINT        NOT NULL,
    missing_skills_count    SMALLINT        NOT NULL,
    transition_difficulty   ENUM('Easy','Moderate','Hard') NOT NULL
                                COMMENT 'Easy>=60%, Moderate 30-59%, Hard<30%',
    computed_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_ct_pair (from_soc_code, to_soc_code),
    INDEX idx_ct_from_soc (from_soc_code),
    INDEX idx_ct_to_soc   (to_soc_code),
    CONSTRAINT fk_ct_from FOREIGN KEY (from_soc_code)
        REFERENCES onet_occupations (onet_soc_code) ON DELETE CASCADE,
    CONSTRAINT fk_ct_to   FOREIGN KEY (to_soc_code)
        REFERENCES onet_occupations (onet_soc_code) ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Precomputed career transition graph. Built by build_career_transitions.py.';

-- ============================================================
-- GROUP 10: Optional — Mock Interviews (Phase-IV)
-- ============================================================

-- ------------------------------------------------------------
-- mock_interview_questions  [Phase-IV]
-- Admin-seeded question bank per skill element.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mock_interview_questions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    element_id      VARCHAR(20)     NOT NULL,
    question_text   TEXT            NOT NULL,
    difficulty      ENUM('Easy','Medium','Hard') NOT NULL,
    model_answer    TEXT            DEFAULT NULL COMMENT 'Reference answer for self-assessment',
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    added_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_miq_element FOREIGN KEY (element_id)
        REFERENCES onet_skill_elements (element_id) ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Mock interview question bank per skill element. Admin-seeded. Phase-IV.';

-- ------------------------------------------------------------
-- student_interview_attempts  [Phase-IV]
-- Student self-attempts at mock interview questions.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_interview_attempts (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL,
    question_id     BIGINT          NOT NULL,
    student_answer  TEXT            DEFAULT NULL,
    self_score      TINYINT         DEFAULT NULL COMMENT '1-5 self-rating',
    attempted_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_sia_user     FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_sia_question FOREIGN KEY (question_id)
        REFERENCES mock_interview_questions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_sia_score CHECK (self_score BETWEEN 1 AND 5 OR self_score IS NULL)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Student mock interview responses. Phase-IV.';

-- ============================================================
-- SEED DATA 1: onet_skill_elements (68 rows from O*NET 31.0)
-- Source: data/processed/career_skill_map.csv (unique element_id + element_name + category)
-- ============================================================

INSERT INTO onet_skill_elements (element_id, element_name, skill_category, dataset_version) VALUES
-- Essential Skills (10)
('2.A.1.a', 'Reading Comprehension',  'Essential',    '31.0'),
('2.A.1.b', 'Active Listening',       'Essential',    '31.0'),
('2.A.1.c', 'Writing',                'Essential',    '31.0'),
('2.A.1.d', 'Speaking',               'Essential',    '31.0'),
('2.A.1.e', 'Mathematics',            'Essential',    '31.0'),
('2.A.1.f', 'Science',                'Essential',    '31.0'),
('2.A.2.a', 'Critical Thinking',      'Essential',    '31.0'),
('2.A.2.b', 'Active Learning',        'Essential',    '31.0'),
('2.A.2.c', 'Learning Strategies',    'Essential',    '31.0'),
('2.A.2.d', 'Monitoring',             'Essential',    '31.0'),
-- Transferable Skills (25)
('2.B.1.a', 'Social Perceptiveness',             'Transferable', '31.0'),
('2.B.1.b', 'Coordination',                      'Transferable', '31.0'),
('2.B.1.c', 'Persuasion',                        'Transferable', '31.0'),
('2.B.1.d', 'Negotiation',                       'Transferable', '31.0'),
('2.B.1.e', 'Instructing',                       'Transferable', '31.0'),
('2.B.1.f', 'Service Orientation',               'Transferable', '31.0'),
('2.B.2.i', 'Complex Problem Solving',           'Transferable', '31.0'),
('2.B.3.a', 'Operations Analysis',               'Transferable', '31.0'),
('2.B.3.b', 'Technology Design',                 'Transferable', '31.0'),
('2.B.3.c', 'Equipment Selection',               'Transferable', '31.0'),
('2.B.3.d', 'Installation',                      'Transferable', '31.0'),
('2.B.3.e', 'Programming',                       'Transferable', '31.0'),
('2.B.3.g', 'Operations Monitoring',             'Transferable', '31.0'),
('2.B.3.h', 'Operation and Control',             'Transferable', '31.0'),
('2.B.3.j', 'Equipment Maintenance',             'Transferable', '31.0'),
('2.B.3.k', 'Troubleshooting',                   'Transferable', '31.0'),
('2.B.3.l', 'Repairing',                         'Transferable', '31.0'),
('2.B.3.m', 'Quality Control Analysis',          'Transferable', '31.0'),
('2.B.4.e', 'Judgment and Decision Making',      'Transferable', '31.0'),
('2.B.4.g', 'Systems Analysis',                  'Transferable', '31.0'),
('2.B.4.h', 'Systems Evaluation',                'Transferable', '31.0'),
('2.B.5.a', 'Time Management',                   'Transferable', '31.0'),
('2.B.5.b', 'Management of Financial Resources', 'Transferable', '31.0'),
('2.B.5.c', 'Management of Material Resources',  'Transferable', '31.0'),
('2.B.5.d', 'Management of Personnel Resources', 'Transferable', '31.0'),
-- Knowledge (33)
('2.C.1.a', 'Administration and Management',    'Knowledge', '31.0'),
('2.C.1.b', 'Administrative',                   'Knowledge', '31.0'),
('2.C.1.c', 'Economics and Accounting',         'Knowledge', '31.0'),
('2.C.1.d', 'Sales and Marketing',              'Knowledge', '31.0'),
('2.C.1.e', 'Customer and Personal Service',    'Knowledge', '31.0'),
('2.C.1.f', 'Personnel and Human Resources',   'Knowledge', '31.0'),
('2.C.10',  'Transportation',                   'Knowledge', '31.0'),
('2.C.2.a', 'Production and Processing',        'Knowledge', '31.0'),
('2.C.2.b', 'Food Production',                  'Knowledge', '31.0'),
('2.C.3.a', 'Computers and Electronics',        'Knowledge', '31.0'),
('2.C.3.b', 'Engineering and Technology',       'Knowledge', '31.0'),
('2.C.3.c', 'Design',                           'Knowledge', '31.0'),
('2.C.3.d', 'Building and Construction',        'Knowledge', '31.0'),
('2.C.3.e', 'Mechanical',                       'Knowledge', '31.0'),
('2.C.4.a', 'Mathematics',                      'Knowledge', '31.0'),
('2.C.4.b', 'Physics',                          'Knowledge', '31.0'),
('2.C.4.c', 'Chemistry',                        'Knowledge', '31.0'),
('2.C.4.d', 'Biology',                          'Knowledge', '31.0'),
('2.C.4.e', 'Psychology',                       'Knowledge', '31.0'),
('2.C.4.f', 'Sociology and Anthropology',       'Knowledge', '31.0'),
('2.C.4.g', 'Geography',                        'Knowledge', '31.0'),
('2.C.5.a', 'Medicine and Dentistry',           'Knowledge', '31.0'),
('2.C.5.b', 'Therapy and Counseling',           'Knowledge', '31.0'),
('2.C.6',   'Education and Training',           'Knowledge', '31.0'),
('2.C.7.a', 'English Language',                 'Knowledge', '31.0'),
('2.C.7.b', 'Foreign Language',                 'Knowledge', '31.0'),
('2.C.7.c', 'Fine Arts',                        'Knowledge', '31.0'),
('2.C.7.d', 'History and Archeology',           'Knowledge', '31.0'),
('2.C.7.e', 'Philosophy and Theology',          'Knowledge', '31.0'),
('2.C.8.a', 'Public Safety and Security',       'Knowledge', '31.0'),
('2.C.8.b', 'Law and Government',               'Knowledge', '31.0'),
('2.C.9.a', 'Telecommunications',               'Knowledge', '31.0'),
('2.C.9.b', 'Communications and Media',         'Knowledge', '31.0');

-- ============================================================
-- SEED DATA 2: onet_occupations
-- Source: data/processed/career_metadata.csv (1,016 rows)
-- Generated from CSV — all values verified from actual data.
-- Description column omitted here (too large for inline SQL);
-- load_onet_data.py loads descriptions from Occupation Data.xlsx.
-- riasec_top3 values of '?' are stored as-is (O*NET convention for N/A).
-- ============================================================

-- NOTE: The full 1,016-row INSERT is generated and loaded by
-- backend/load_onet_data.py which reads career_metadata.csv directly.
-- This keeps schema.sql maintainable and avoids a 50KB inline block.
-- A small representative sample is shown below for verification:

-- SAMPLE (first 5 rows — loader inserts all 1,016):
-- INSERT INTO onet_occupations (onet_soc_code, occupation_title, job_zone, riasec_top1, riasec_top2, riasec_top3)
-- VALUES
--   ('11-1011.00', 'Chief Executives',                5, 'E', 'C', '?'),
--   ('11-1011.03', 'Chief Sustainability Officers',   5, 'E', 'I', 'C'),
--   ('11-1021.00', 'General and Operations Managers', 4, 'E', 'C', '?'),
--   ('11-1031.00', 'Legislators',                     4, 'E', 'S', 'C'),
--   ('11-2011.00', 'Advertising and Promotions Managers', 3, 'E', 'A', 'C');
--
-- Full data loaded by: backend/load_onet_data.py

-- ============================================================
-- SEED DATA 3: onet_career_skill_map (50,261 rows)
-- NOT inlined — loaded by backend/load_onet_data.py
-- Source: data/processed/career_skill_map.csv
-- ============================================================

-- ============================================================
-- SEED DATA 4: riasec_questions (starter bank — 18 questions, 3 per dimension)
-- Admin can add/retire questions via the admin panel.
-- ============================================================

INSERT INTO riasec_questions (question_text, riasec_dimension, display_order) VALUES
-- Realistic (R) — hands-on / mechanical
('I enjoy working with tools, machines, or technology to build or fix things.', 'R', 1),
('I prefer physical or outdoor activities over desk-based work.',               'R', 2),
('I am good at working with my hands to create or repair physical objects.',    'R', 3),
-- Investigative (I) — analytical / scientific
('I enjoy solving complex problems and figuring out how things work.',           'I', 4),
('I like researching, analyzing data, and drawing conclusions from evidence.',   'I', 5),
('I prefer asking questions and exploring ideas over following set procedures.', 'I', 6),
-- Artistic (A) — creative / expressive
('I enjoy expressing myself creatively through art, writing, music, or design.', 'A', 7),
('I prefer open-ended tasks where I can use my imagination.',                    'A', 8),
('I like work that allows me to be original and innovative.',                    'A', 9),
-- Social (S) — helping / teaching
('I enjoy helping, teaching, or supporting other people.',                       'S', 10),
('I prefer working with people over working alone with data or machines.',       'S', 11),
('I am good at communicating and understanding others'' feelings.',              'S', 12),
-- Enterprising (E) — leading / persuading
('I enjoy leading projects and persuading others to follow my vision.',          'E', 13),
('I like taking initiative and making decisions that affect others.',            'E', 14),
('I am comfortable with competition and enjoy achieving ambitious goals.',       'E', 15),
-- Conventional (C) — organizing / detail
('I enjoy organizing information, following clear procedures, and keeping records.', 'C', 16),
('I prefer structured tasks with clear rules and measurable outcomes.',              'C', 17),
('I am good at paying attention to detail and completing tasks accurately.',         'C', 18);

-- ============================================================
-- END OF SCHEMA
-- ============================================================
-- Summary:
--   Tables created : 24
--   Indexes        : 7 (plus PKs and UKs)
--   Constraints    : 20 CHECK + all FKs
--   Seed data      : 68 skill elements + 18 RIASEC questions (inline)
--                    1,016 occupations + 50,261 map rows (via load_onet_data.py)
-- ============================================================
