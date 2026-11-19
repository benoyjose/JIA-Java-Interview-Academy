-- Difficulty-based question bank and learner assessment tracking.
-- Question content is seeded by approved editors; this migration creates schema only.
CREATE TABLE questions (
    id UUID PRIMARY KEY,
    academy_id UUID NOT NULL REFERENCES academies(id),
    technology_id UUID NOT NULL REFERENCES technologies(id),
    category_id UUID REFERENCES categories(id),
    lesson_id UUID REFERENCES lessons(id),
    question_type VARCHAR(24) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_revision INTEGER NOT NULL DEFAULT 1,
    tags TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_question_type CHECK (question_type IN ('SINGLE_SELECT','MULTI_SELECT','TRUE_FALSE','OUTPUT_PREDICTION','DEBUGGING','CODING','SQL','SCENARIO')),
    CONSTRAINT chk_question_difficulty CHECK (difficulty IN ('BEGINNER','NOVICE','PROFESSIONAL','EXPERT')),
    CONSTRAINT chk_question_status CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED','ARCHIVED'))
);
CREATE INDEX idx_questions_quiz_filter
    ON questions(academy_id, technology_id, category_id, difficulty, status);

CREATE TABLE question_revisions (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    prompt_json JSONB NOT NULL,
    options_json JSONB,
    answer_json JSONB NOT NULL,
    explanation TEXT NOT NULL,
    hint TEXT,
    source_type VARCHAR(30) NOT NULL,
    source_description TEXT,
    attribution TEXT,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_question_revision UNIQUE(question_id, revision_number),
    CONSTRAINT chk_question_source CHECK (source_type IN ('USER_ORIGINAL','ADMIN_AUTHORED','EXTERNAL_REFERENCE'))
);

CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    academy_id UUID NOT NULL REFERENCES academies(id),
    technology_id UUID REFERENCES technologies(id),
    category_id UUID REFERENCES categories(id),
    difficulty VARCHAR(20) NOT NULL,
    mode VARCHAR(24) NOT NULL DEFAULT 'PRACTICE',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    question_count INTEGER NOT NULL,
    correct_count INTEGER,
    score_percent NUMERIC(5,2),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    duration_seconds INTEGER,
    CONSTRAINT chk_attempt_difficulty CHECK (difficulty IN ('BEGINNER','NOVICE','PROFESSIONAL','EXPERT')),
    CONSTRAINT chk_attempt_mode CHECK (mode IN ('PRACTICE','TIMED_MOCK','DAILY_CHALLENGE','WEAK_AREA')),
    CONSTRAINT chk_attempt_status CHECK (status IN ('IN_PROGRESS','SUBMITTED','ABANDONED')),
    CONSTRAINT chk_attempt_question_count CHECK (question_count BETWEEN 1 AND 100)
);
CREATE INDEX idx_quiz_attempts_user_level_date
    ON quiz_attempts(user_id, academy_id, difficulty, started_at DESC);
CREATE INDEX idx_quiz_attempts_user_topic_date
    ON quiz_attempts(user_id, technology_id, category_id, started_at DESC);

-- Snapshot revision IDs into the attempt so later edits cannot change historical grading.
CREATE TABLE quiz_attempt_questions (
    attempt_id UUID NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id),
    question_revision_id UUID NOT NULL REFERENCES question_revisions(id),
    position INTEGER NOT NULL,
    max_marks NUMERIC(6,2) NOT NULL DEFAULT 1,
    PRIMARY KEY(attempt_id, position),
    UNIQUE(attempt_id, question_id)
);

CREATE TABLE quiz_attempt_answers (
    attempt_id UUID NOT NULL,
    position INTEGER NOT NULL,
    response_json JSONB,
    is_correct BOOLEAN,
    marks_awarded NUMERIC(6,2),
    time_spent_ms INTEGER,
    answered_at TIMESTAMPTZ,
    PRIMARY KEY(attempt_id, position),
    FOREIGN KEY(attempt_id, position)
        REFERENCES quiz_attempt_questions(attempt_id, position) ON DELETE CASCADE
);

CREATE INDEX idx_attempt_answers_performance
    ON quiz_attempt_answers(attempt_id, is_correct);
