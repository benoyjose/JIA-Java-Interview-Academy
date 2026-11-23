-- Expand supported learner assessment modes while preserving existing attempt history.
ALTER TABLE quiz_attempts DROP CONSTRAINT chk_attempt_mode;
ALTER TABLE quiz_attempts ADD CONSTRAINT chk_attempt_mode
 CHECK (mode IN ('PRACTICE','TIMED_MOCK','DAILY_CHALLENGE','WEAK_AREA','TOPIC_ASSESSMENT','MIXED_CHALLENGE','RETRY_INCORRECT'));
CREATE INDEX idx_attempt_question_retry_lookup ON quiz_attempt_questions(question_id,attempt_id);
