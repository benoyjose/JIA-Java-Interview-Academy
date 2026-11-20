-- One immutable daily challenge attempt per learner and UTC calendar day.
ALTER TABLE quiz_attempts ADD COLUMN challenge_date DATE;

CREATE UNIQUE INDEX uq_quiz_attempts_daily_challenge
    ON quiz_attempts(user_id, academy_id, challenge_date)
    WHERE mode = 'DAILY_CHALLENGE' AND challenge_date IS NOT NULL;

CREATE INDEX idx_quiz_attempts_daily_challenge_date
    ON quiz_attempts(challenge_date)
    WHERE mode = 'DAILY_CHALLENGE';
