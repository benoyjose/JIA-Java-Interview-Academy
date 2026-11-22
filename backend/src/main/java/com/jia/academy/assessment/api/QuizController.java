package com.jia.academy.assessment.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.security.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class QuizController {
    private static final UUID JIA_ACADEMY = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final List<String> LEVELS = List.of("BEGINNER", "NOVICE", "PROFESSIONAL", "EXPERT");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public QuizController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping("/quizzes/availability")
    public Map<String, Object> availability(
            @RequestParam String difficulty,
            @RequestParam(required = false) String technologySlug,
            @RequestParam(required = false) String categorySlug) {
        Filters filters = filters(difficulty, technologySlug, categorySlug, JIA_ACADEMY);
        long count = jdbc.queryForObject(questionCountSql(filters), Long.class, countArgs(filters, JIA_ACADEMY));
        return Map.of("difficulty", difficulty, "availableQuestionCount", count);
    }

    @PostMapping("/quiz-attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> start(@AuthenticationPrincipal CurrentUser user, @RequestBody Map<String, Object> body) {
        requireUser(user);
        String difficulty = requiredString(body, "difficulty");
        String technologySlug = optionalString(body, "technologySlug");
        String categorySlug = optionalString(body, "categorySlug");
        int count = body.get("questionCount") instanceof Number n ? n.intValue() : 10;
        String mode = body.get("mode") instanceof String s ? s : "PRACTICE";
        if (count < 1 || count > 100) throw badRequest("questionCount must be between 1 and 100");
        Integer durationLimitSeconds = null;
        Instant deadlineAt = null;
        if ("TIMED_MOCK".equals(mode)) {
            Object rawDuration = body.get("durationSeconds");
            if (!(rawDuration instanceof Number number) || number.doubleValue() != number.intValue()
                    || number.intValue() < 60 || number.intValue() > 21600) {
                throw badRequest("durationSeconds must be between 60 and 21600");
            }
            durationLimitSeconds = number.intValue();
        } else if (!"DAILY_CHALLENGE".equals(mode) && !"WEAK_AREA".equals(mode)) {
            validateMode(mode);
        }
        UUID academyId = primaryAcademyId(user);
        Filters filters = filters(difficulty, technologySlug, categorySlug, academyId);
        if ("DAILY_CHALLENGE".equals(mode)) {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(? || ':' || (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date::text, 0))",
                    rs -> {}, user.id().toString());
            List<UUID> existing = jdbc.query("""
                SELECT id FROM quiz_attempts
                WHERE user_id=? AND academy_id=? AND mode='DAILY_CHALLENGE' AND challenge_date=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date
                """, (rs,n) -> rs.getObject(1, UUID.class), user.id(), academyId);
            if (!existing.isEmpty()) return dailyChallengeResponse(existing.getFirst(), user.id(), academyId);
        }

        String sql = """
            SELECT q.id AS question_id, q.question_type, q.difficulty,
                   qr.id AS revision_id, qr.prompt_json::text AS prompt_json,
                   qr.options_json::text AS options_json, qr.hint
            FROM questions q
            JOIN technologies t ON t.id = q.technology_id AND t.academy_id = ?
            JOIN question_revisions qr ON qr.question_id = q.id AND qr.revision_number = q.current_revision
            WHERE q.academy_id = ? AND q.status = 'PUBLISHED' AND q.difficulty = ?
            """ + filterSql(filters);
        if ("WEAK_AREA".equals(mode)) {
            sql += """
                 ORDER BY COALESCE((
                     SELECT SUM(CASE WHEN paa.is_correct THEN 1 ELSE 0 END)::numeric / NULLIF(COUNT(*), 0)
                     FROM quiz_attempts pa
                     JOIN quiz_attempt_questions paq ON paq.attempt_id = pa.id
                     JOIN quiz_attempt_answers paa ON paa.attempt_id = paq.attempt_id AND paa.position = paq.position
                     JOIN questions pq ON pq.id = paq.question_id
                     WHERE pa.user_id = ? AND pa.academy_id = q.academy_id
                       AND pa.status = 'SUBMITTED' AND pq.category_id = q.category_id
                 ), 1.0) ASC, random()
                """;
        } else {
            sql += " ORDER BY random()";
        }
        sql += " LIMIT ?";
        List<QuestionRow> selected = jdbc.query(sql, this::questionRow, startArgs(filters, count, user.id(), mode, academyId));
        if (selected.size() < count) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Not enough published questions for selected filters. Available: " + selected.size());
        }

        UUID attemptId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        deadlineAt = durationLimitSeconds == null ? null : startedAt.plusSeconds(durationLimitSeconds);
        jdbc.update("""
            INSERT INTO quiz_attempts(id,user_id,academy_id,technology_id,category_id,difficulty,mode,status,
                question_count,started_at,duration_limit_seconds,deadline_at,challenge_date)
            VALUES (?,?,?,?,?,?,?,'IN_PROGRESS',?,?,?,?,CASE WHEN ?='DAILY_CHALLENGE' THEN (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date ELSE NULL END)
            """, attemptId, user.id(), academyId, filters.technologyId(), filters.categoryId(),
                difficulty, mode, count, Timestamp.from(startedAt), durationLimitSeconds,
                deadlineAt == null ? null : Timestamp.from(deadlineAt), mode);

        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            QuestionRow q = selected.get(i);
            int position = i + 1;
            jdbc.update("""
                INSERT INTO quiz_attempt_questions(attempt_id,question_id,question_revision_id,position,max_marks)
                VALUES (?,?,?,?,1)
                """, attemptId, q.questionId(), q.revisionId(), position);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("position", position);
            item.put("questionId", q.questionId());
            item.put("revisionId", q.revisionId());
            item.put("questionType", q.questionType());
            item.put("prompt", json(q.promptJson()));
            item.put("options", json(q.optionsJson()));
            item.put("hint", q.hint());
            item.put("difficulty", q.difficulty());
            questions.add(item);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("attemptId", attemptId);
        response.put("difficulty", difficulty);
        response.put("mode", mode);
        response.put("questionCount", count);
        response.put("durationLimitSeconds", durationLimitSeconds);
        response.put("deadlineAt", deadlineAt == null ? null : deadlineAt.toString());
        response.put("challengeDate", "DAILY_CHALLENGE".equals(mode)
                ? jdbc.queryForObject("SELECT challenge_date::text FROM quiz_attempts WHERE id=?", String.class, attemptId) : null);
        response.put("questions", questions);
        return response;
    }

    @PostMapping("/quiz-attempts/{attemptId}/answers")
    @Transactional
    public Map<String, Object> saveAnswers(@AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID attemptId, @RequestBody Map<String, Object> body) {
        requireUser(user);
        Object raw = body.get("answers");
        if (!(raw instanceof List<?> answers) || answers.isEmpty() || answers.size() > 100) {
            throw badRequest("answers must contain between 1 and 100 items");
        }
        var attempts = jdbc.query("""
            SELECT mode, status, deadline_at FROM quiz_attempts
            WHERE id=? AND user_id=? AND academy_id=? FOR UPDATE
            """, (rs,n) -> new Object[]{rs.getString("mode"), rs.getString("status"), rs.getTimestamp("deadline_at")},
                attemptId, user.id(), primaryAcademyId(user));
        if (attempts.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz attempt not found");
        Object[] attempt = attempts.getFirst();
        if (!"TIMED_MOCK".equals(attempt[0])) throw badRequest("Answer autosave is only available for timed mock attempts");
        if (!"IN_PROGRESS".equals(attempt[1])) throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz attempt is already closed");
        Timestamp deadline = (Timestamp) attempt[2];
        if (deadline == null || !Instant.now().isBefore(deadline.toInstant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Timed mock deadline has passed");
        }
        Set<Integer> positions = new HashSet<>();
        Set<Integer> expectedPositions = new HashSet<>(jdbc.queryForList(
                "SELECT position FROM quiz_attempt_questions WHERE attempt_id=?", Integer.class, attemptId));
        for (Object item : answers) {
            if (!(item instanceof Map<?,?> map) || !(map.get("position") instanceof Number position)
                    || !(map.get("response") instanceof Map<?,?> response)) throw badRequest("Each answer requires position and response");
            int p = position.intValue();
            if (p < 1 || position.doubleValue() != p || !positions.add(p)) throw badRequest("Answer positions must be unique positive integers");
            validateTimeSpentMs(map.get("timeSpentMs"));
            if (!expectedPositions.contains(p)) throw badRequest("Answer position is not part of this attempt");
            Map<String,Object> normalized = new HashMap<>();
            response.forEach((k,v) -> normalized.put(String.valueOf(k),v));
            jdbc.update("""
                INSERT INTO quiz_attempt_answers(attempt_id,position,response_json,time_spent_ms,answered_at)
                VALUES (?,?,?::jsonb,?,?)
                ON CONFLICT (attempt_id,position) DO UPDATE SET
                  response_json=EXCLUDED.response_json,
                  time_spent_ms=EXCLUDED.time_spent_ms,
                  answered_at=EXCLUDED.answered_at
                """, attemptId, p, writeJson(normalized),
                map.get("timeSpentMs") instanceof Number n ? n.intValue() : null, Timestamp.from(Instant.now()));
        }
         return Map.of("attemptId", attemptId, "savedCount", positions.size());
    }

    @PostMapping("/quiz-attempts/{attemptId}/submit")
    @Transactional
    public Map<String, Object> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID attemptId, @RequestBody Map<String, Object> body) {
        requireUser(user);
        Object rawAnswers = body.get("answers");
        if (!(rawAnswers instanceof List<?> answers) || answers.size() > 100) {
            throw badRequest("answers must be an array");
        }
        var attempts = jdbc.query("""
            SELECT id, difficulty, started_at, mode, deadline_at FROM quiz_attempts
            WHERE id = ? AND user_id = ? AND academy_id = ? FOR UPDATE
            """, (rs, n) -> new AttemptRow(rs.getObject("id", UUID.class),
                rs.getString("difficulty"), rs.getTimestamp("started_at").toInstant(),
                rs.getString("mode"), rs.getTimestamp("deadline_at") == null ? null : rs.getTimestamp("deadline_at").toInstant()), attemptId, user.id(), primaryAcademyId(user));
        if (attempts.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz attempt not found");
        AttemptRow attempt = attempts.getFirst();
        String status = jdbc.queryForObject("SELECT status FROM quiz_attempts WHERE id = ? AND user_id=? AND academy_id=?", String.class, attemptId, user.id(), primaryAcademyId(user));
        if (!"IN_PROGRESS".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz attempt is already closed");
        boolean timedMock = "TIMED_MOCK".equals(attempt.mode());
        boolean timedOut = timedMock && (attempt.deadlineAt() == null || !Instant.now().isBefore(attempt.deadlineAt()));

        Map<Integer, Map<String, Object>> submitted = new HashMap<>();
        for (Object item : answers) {
            if (!(item instanceof Map<?, ?> map) || !(map.get("position") instanceof Number position)
                    || !(map.get("response") instanceof Map<?, ?> response)) {
                throw badRequest("Each answer requires position and response");
            }
            int answerPosition = position.intValue();
            if (position.doubleValue() != answerPosition || answerPosition < 1) {
                throw badRequest("Answer position must be a positive integer");
            }
            validateTimeSpentMs(map.get("timeSpentMs"));
            Map<String, Object> normalized = new HashMap<>();
            response.forEach((k, v) -> normalized.put(String.valueOf(k), v));
            if (submitted.put(answerPosition, normalized) != null) throw badRequest("Duplicate answer position");
        }

        if (timedMock) {
            if (timedOut) submitted.clear();
            List<Map<String,Object>> saved = jdbc.query("""
                SELECT position, response_json::text AS response_json, time_spent_ms
                FROM quiz_attempt_answers WHERE attempt_id=?
                """, (rs,n) -> {
                    Map<String,Object> item = new HashMap<>();
                    item.put("position", rs.getInt("position"));
                    item.put("response", readMap(rs.getString("response_json")));
                    item.put("timeSpentMs", rs.getObject("time_spent_ms"));
                    return item;
                }, attemptId);
            for (Map<String,Object> item : saved) {
                int p = (Integer) item.get("position");
                if (timedOut || !submitted.containsKey(p)) submitted.put(p, (Map<String,Object>) item.get("response"));
            }
        }

        List<GradingRow> rows = jdbc.query("""
            SELECT aq.position, q.question_type, qr.answer_json::text AS answer_json, qr.explanation
            FROM quiz_attempt_questions aq
            JOIN questions q ON q.id = aq.question_id
            JOIN question_revisions qr ON qr.id = aq.question_revision_id
            WHERE aq.attempt_id = ? ORDER BY aq.position
            """, (rs, n) -> new GradingRow(rs.getInt("position"), rs.getString("question_type"),
                rs.getString("answer_json"), rs.getString("explanation")), attemptId);
        Set<Integer> expectedPositions = new HashSet<>();
        for (GradingRow row : rows) expectedPositions.add(row.position());
        if (!timedMock) validateCompleteAnswers(expectedPositions, submitted.keySet());
        else if (submitted.keySet().stream().anyMatch(p -> !expectedPositions.contains(p))) throw badRequest("Answer position is not part of this attempt");

        int correct = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (GradingRow row : rows) {
            Map<String, Object> response = submitted.getOrDefault(row.position(), Map.of());
            Map<String, Object> answer = readMap(row.answerJson());
            boolean isCorrect = submitted.containsKey(row.position()) && isCorrect(row.questionType(), response, answer);
            if (isCorrect) correct++;
            Object time = null;
            if (timedMock) {
                time = jdbc.query("SELECT time_spent_ms FROM quiz_attempt_answers WHERE attempt_id=? AND position=?",
                        rs -> rs.next() ? rs.getObject(1) : null, attemptId, row.position());
            } else {
                time = answers.stream().filter(a -> a instanceof Map<?, ?> m
                        && m.get("position") instanceof Number p && p.intValue() == row.position())
                        .map(a -> ((Map<?, ?>) a).get("timeSpentMs")).findFirst().orElse(null);
            }
            jdbc.update("""
                INSERT INTO quiz_attempt_answers(attempt_id,position,response_json,is_correct,marks_awarded,time_spent_ms,answered_at)
                VALUES (?,?,?::jsonb,?,?,?,?)
                ON CONFLICT (attempt_id,position) DO UPDATE SET
                  response_json=EXCLUDED.response_json,
                  is_correct=EXCLUDED.is_correct,
                  marks_awarded=EXCLUDED.marks_awarded,
                  time_spent_ms=EXCLUDED.time_spent_ms,
                  answered_at=EXCLUDED.answered_at
                """, attemptId, row.position(), writeJson(response), isCorrect, isCorrect ? 1 : 0,
                    time instanceof Number n ? n.intValue() : null, Timestamp.from(Instant.now()));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("position", row.position());
            result.put("isCorrect", isCorrect);
            result.put("explanation", row.explanation());
            results.add(result);
        }
        int total = rows.size();
        double score = total == 0 ? 0 : Math.round((correct * 10000.0) / total) / 100.0;
        Instant submittedAt = Instant.now();
        jdbc.update("""
            UPDATE quiz_attempts SET status='SUBMITTED', correct_count=?, score_percent=?,
              submitted_at=?, duration_seconds=? WHERE id=?
            """, correct, score, Timestamp.from(submittedAt), Math.max(0, submittedAt.getEpochSecond() - attempt.startedAt().getEpochSecond()), attemptId);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("attemptId", attemptId);
        result.put("difficulty", attempt.difficulty());
        result.put("questionCount", total);
        result.put("correctCount", correct);
        result.put("scorePercent", score);
        result.put("submittedAt", submittedAt.toString());
        result.put("timedOut", timedOut);
        result.put("answers", results);
        return result;
    }

    @GetMapping("/me/quiz-performance")
    public Map<String, Object> performance(@AuthenticationPrincipal CurrentUser user) {
        requireUser(user);
        List<Map<String, Object>> levels = new ArrayList<>();
        for (String level : LEVELS) {
            var rows = jdbc.query("""
                SELECT COUNT(*) AS attempts, COALESCE(SUM(question_count),0) AS questions_attempted,
                       COALESCE(SUM(correct_count),0) AS correct_answers,
                       ROUND((SUM(correct_count)::numeric / NULLIF(SUM(question_count),0))*100,2) AS accuracy_percent, MAX(score_percent) AS best_score_percent,
                       MAX(submitted_at) AS latest_attempt_at
                FROM quiz_attempts WHERE user_id=? AND academy_id=? AND difficulty=? AND status='SUBMITTED'
                """, (rs,n) -> {
                    Map<String,Object> item = new LinkedHashMap<>();
                    item.put("difficulty", level);
                    item.put("attempts", rs.getInt("attempts"));
                    item.put("questionsAttempted", rs.getInt("questions_attempted"));
                    item.put("correctAnswers", rs.getInt("correct_answers"));
                    item.put("accuracyPercent", rs.getObject("accuracy_percent"));
                    item.put("bestScorePercent", rs.getObject("best_score_percent"));
                    var latest = rs.getTimestamp("latest_attempt_at");
                    item.put("latestAttemptAt", latest == null ? null : latest.toInstant().toString());
                    return item;
                }, user.id(), primaryAcademyId(user), level);
            levels.add(rows.getFirst());
        }
        List<Map<String,Object>> topics = jdbc.query("""
            SELECT t.slug AS technology_slug, c.slug AS category_slug, a.difficulty,
                   COUNT(*) AS attempts, ROUND((SUM(a.correct_count)::numeric / NULLIF(SUM(a.question_count),0))*100,2) AS accuracy_percent
            FROM quiz_attempts a
            LEFT JOIN technologies t ON t.id=a.technology_id
            LEFT JOIN categories c ON c.id=a.category_id
            WHERE a.user_id=? AND a.academy_id=? AND a.status='SUBMITTED'
            GROUP BY t.slug,c.slug,a.difficulty ORDER BY t.slug,c.slug,a.difficulty
            """, (rs,n) -> {
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("technologySlug", rs.getString("technology_slug"));
                item.put("categorySlug", rs.getString("category_slug"));
                item.put("difficulty", rs.getString("difficulty"));
                item.put("attempts", rs.getInt("attempts"));
                item.put("accuracyPercent", rs.getBigDecimal("accuracy_percent"));
                return item;
            }, user.id(), primaryAcademyId(user));
        return Map.of("byDifficulty", levels, "byTopic", topics);
    }

    @GetMapping("/me/quiz-attempts")
    public List<Map<String,Object>> history(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required=false) String difficulty,
            @RequestParam(defaultValue="20") int limit) {
        requireUser(user);
        if (limit < 1 || limit > 100) throw badRequest("limit must be between 1 and 100");
        if (difficulty != null && !LEVELS.contains(difficulty)) throw badRequest("Invalid difficulty");
        String sql = """
            SELECT a.id,a.difficulty,t.slug AS technology_slug,c.slug AS category_slug,a.question_count,
                   a.correct_count,a.score_percent,a.started_at,a.status
            FROM quiz_attempts a LEFT JOIN technologies t ON t.id=a.technology_id
            LEFT JOIN categories c ON c.id=a.category_id
            WHERE a.user_id=? AND a.academy_id=? AND (? IS NULL OR a.difficulty=?)
            ORDER BY a.started_at DESC LIMIT ?
            """;
        return jdbc.query(sql, (rs,n) -> {
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("attemptId", rs.getObject("id", UUID.class));
            item.put("difficulty", rs.getString("difficulty"));
            item.put("technologySlug", rs.getString("technology_slug"));
            item.put("categorySlug", rs.getString("category_slug"));
            item.put("questionCount", rs.getInt("question_count"));
            item.put("correctCount", (Integer) rs.getObject("correct_count"));
            item.put("scorePercent", rs.getBigDecimal("score_percent"));
            item.put("startedAt", rs.getTimestamp("started_at").toInstant().toString());
            item.put("status", rs.getString("status"));
            return item;
        }, user.id(), primaryAcademyId(user), difficulty, difficulty, limit);
    }

    private Map<String,Object> dailyChallengeResponse(UUID attemptId, UUID userId, UUID academyId) {
        var attempts = jdbc.query("""
            SELECT difficulty, question_count, started_at, challenge_date
            FROM quiz_attempts WHERE id=? AND user_id=? AND academy_id=?
            """, (rs,n) -> new Object[]{rs.getString("difficulty"), rs.getInt("question_count"),
                rs.getTimestamp("started_at"), rs.getString("challenge_date")}, attemptId, userId, academyId);
        if (attempts.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Daily challenge not found");
        Object[] a = attempts.getFirst();
        List<Map<String,Object>> questions = jdbc.query("""
            SELECT aq.position, q.id AS question_id, aq.question_revision_id AS revision_id,
                   q.question_type, qr.prompt_json::text AS prompt_json, qr.options_json::text AS options_json,
                   qr.hint, q.difficulty
            FROM quiz_attempt_questions aq
            JOIN questions q ON q.id=aq.question_id
            JOIN question_revisions qr ON qr.id=aq.question_revision_id
            WHERE aq.attempt_id=? ORDER BY aq.position
            """, (rs,n) -> {
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("position", rs.getInt("position"));
                item.put("questionId", rs.getObject("question_id", UUID.class));
                item.put("revisionId", rs.getObject("revision_id", UUID.class));
                item.put("questionType", rs.getString("question_type"));
                item.put("prompt", json(rs.getString("prompt_json")));
                item.put("options", json(rs.getString("options_json")));
                item.put("hint", rs.getString("hint"));
                item.put("difficulty", rs.getString("difficulty"));
                return item;
            }, attemptId);
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("attemptId", attemptId);
        response.put("difficulty", a[0]);
        response.put("mode", "DAILY_CHALLENGE");
        response.put("questionCount", a[1]);
        response.put("durationLimitSeconds", null);
        response.put("deadlineAt", null);
        response.put("challengeDate", a[3]);
        response.put("questions", questions);
        return response;
    }

    static void validateMode(String mode) {
        if (!"PRACTICE".equals(mode) && !"DAILY_CHALLENGE".equals(mode) && !"WEAK_AREA".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quiz mode is not available yet: " + mode);
        }
    }

    static void validateCompleteAnswers(Set<Integer> expectedPositions, Set<Integer> submittedPositions) {
        if (expectedPositions.isEmpty() || !submittedPositions.equals(expectedPositions)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Submit exactly one answer for every question in this attempt");
        }
    }

    static void validateTimeSpentMs(Object value) {
        if (value == null) return;
        if (!(value instanceof Number number)
                || number.doubleValue() < 0
                || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "timeSpentMs must be a non-negative integer");
        }
    }

    private Filters filters(String difficulty, String technologySlug, String categorySlug, UUID academyId) {
        if (!LEVELS.contains(difficulty)) throw badRequest("Invalid difficulty");
        UUID technologyId = null;
        UUID categoryId = null;
        if (technologySlug != null) {
            var ids = jdbc.query("SELECT id FROM technologies WHERE academy_id=? AND slug=? AND status='PUBLISHED'",
                    (rs,n) -> rs.getObject("id", UUID.class), academyId, technologySlug);
            if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Technology not found");
            technologyId = ids.getFirst();
        }
        if (categorySlug != null) {
            var rows = jdbc.query("""
                SELECT c.id,c.technology_id FROM categories c JOIN technologies t ON t.id=c.technology_id
                WHERE c.slug=? AND c.status='PUBLISHED' AND t.academy_id=?
                """, (rs,n) -> new UUID[]{rs.getObject("id", UUID.class), rs.getObject("technology_id", UUID.class)},
                    categorySlug, academyId);
            if (rows.isEmpty() || (technologyId != null && !technologyId.equals(rows.getFirst()[1]))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found for selected technology");
            }
            categoryId = rows.getFirst()[0];
            technologyId = rows.getFirst()[1];
        }
        return new Filters(difficulty, technologyId, categoryId);
    }

    private String filterSql(Filters f) {
        return (f.technologyId() == null ? "" : " AND q.technology_id = ?")
                + (f.categoryId() == null ? "" : " AND q.category_id = ?");
    }
    private Object[] countArgs(Filters f, UUID academyId) {
        List<Object> args = new ArrayList<>(List.of(academyId, academyId, f.difficulty()));
        if (f.technologyId() != null) args.add(f.technologyId());
        if (f.categoryId() != null) args.add(f.categoryId());
        return args.toArray();
    }
    private String questionCountSql(Filters f) {
        return "SELECT COUNT(*) FROM questions q JOIN technologies t ON t.id=q.technology_id AND t.academy_id=? "
                + "JOIN question_revisions qr ON qr.question_id=q.id AND qr.revision_number=q.current_revision "
                + "WHERE q.academy_id=? AND q.status='PUBLISHED' AND q.difficulty=? " + filterSql(f);
    }
    private Object[] startArgs(Filters f, int count, UUID userId, String mode, UUID academyId) {
        List<Object> args = new ArrayList<>(List.of(academyId, academyId, f.difficulty()));
        if (f.technologyId() != null) args.add(f.technologyId());
        if (f.categoryId() != null) args.add(f.categoryId());
        if ("WEAK_AREA".equals(mode)) args.add(userId);
        args.add(count);
        return args.toArray();
    }
    private QuestionRow questionRow(ResultSet rs, int n) throws SQLException {
        return new QuestionRow(rs.getObject("question_id", UUID.class), rs.getString("question_type"),
                rs.getString("difficulty"), rs.getObject("revision_id", UUID.class),
                rs.getString("prompt_json"), rs.getString("options_json"), rs.getString("hint"));
    }
    private Object json(String value) {
        if (value == null) return null;
        try { return mapper.readValue(value, Object.class); }
        catch (Exception ex) { throw new IllegalStateException("Invalid question JSON", ex); }
    }
    private Map<String,Object> readMap(String value) {
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Invalid answer key JSON", ex); }
    }
    private String writeJson(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Unable to serialize quiz response", ex); }
    }
    private boolean isCorrect(String type, Map<String,Object> response, Map<String,Object> answer) {
        return QuizAnswerEvaluator.isCorrect(type, response, answer);
    }

    private static UUID primaryAcademyId(CurrentUser user) {
        if (user == null || user.academyIds() == null || user.academyIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active academy membership is required");
        }
        return user.academyIds().getFirst();
    }

    private void requireUser(CurrentUser user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
    }
    private String requiredString(Map<String,Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof String s) || s.isBlank()) throw badRequest(key + " is required");
        return s;
    }
    private String optionalString(Map<String,Object> body, String key) {
        return body.get(key) instanceof String s && !s.isBlank() ? s : null;
    }
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private record Filters(String difficulty, UUID technologyId, UUID categoryId) {}
    private record QuestionRow(UUID questionId, String questionType, String difficulty, UUID revisionId,
                               String promptJson, String optionsJson, String hint) {}
    private record AttemptRow(UUID id, String difficulty, Instant startedAt, String mode, Instant deadlineAt) {}
    private record GradingRow(int position, String questionType, String answerJson, String explanation) {}
}
