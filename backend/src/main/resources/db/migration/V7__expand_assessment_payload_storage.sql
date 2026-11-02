ALTER TABLE career_assessments
    MODIFY COLUMN assessment_json MEDIUMTEXT NOT NULL,
    MODIFY COLUMN recommendations_json MEDIUMTEXT NOT NULL;
