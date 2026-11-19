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
    private static final List<String> MODES = List.of("PRACTICE", "TIMED_MOCK", "DAILY_CHALLENGE", "WEAK_AREA");
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
        Filters filters = filters(difficulty, technologySlug, categorySlug);
        long count = jdbc.queryForObject(questionCountSql(filters), Long.class, countArgs(filters));
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
        if (!MODES.contains(mode)) throw badRequest("Unsupported quiz mode");
        Filters filters = filters(difficulty, technologySlug, categorySlug);

        String sql = """
            SELECT q.id AS question_id, q.question_type, q.difficulty,
                   qr.id AS revision_id, qr.prompt_json::text AS prompt_json,
                   qr.options_json::text AS options_json, qr.hint
            FROM questions q
            JOIN technologies t ON t.id = q.technology_id AND t.academy_id = ?
            JOIN question_revisions qr ON qr.question_id = q.id AND qr.revision_number = q.current_revision
            WHERE q.academy_id = ? AND q.status = 'PUBLISHED' AND q.difficulty = ?
            """ + filterSql(filters) + " ORDER BY random() LIMIT ?";
        List<QuestionRow> selected = jdbc.query(sql, this::questionRow, startArgs(filters, count));
        if (selected.size() < count) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Not enough published questions for selected filters. Available: " + selected.size());
        }

        UUID attemptId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        jdbc.update("""
            INSERT INTO quiz_attempts(id,user_id,academy_id,technology_id,category_id,difficulty,mode,status,question_count,started_at)
            VALUES (?,?,?,?,?,?,?,'IN_PROGRESS',?,?)
            """, attemptId, user.id(), JIA_ACADEMY, filters.technologyId(), filters.categoryId(),
                difficulty, mode, count, Timestamp.from(startedAt));

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
        return Map.of("attemptId", attemptId, "difficulty", difficulty, "mode", mode,
                "questionCount", count, "questions", questions);
    }

    @PostMapping("/quiz-attempts/{attemptId}/submit")
    @Transactional
    public Map<String, Object> submit(@AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID attemptId, @RequestBody Map<String, Object> body) {
        requireUser(user);
        Object rawAnswers = body.get("answers");
        if (!(rawAnswers instanceof List<?> answers) || answers.isEmpty()) {
            throw badRequest("answers must be a non-empty array");
        }
        var attempts = jdbc.query("""
            SELECT id, difficulty, started_at FROM quiz_attempts
            WHERE id = ? AND user_id = ? FOR UPDATE
            """, (rs, n) -> new AttemptRow(rs.getObject("id", UUID.class),
                rs.getString("difficulty"), rs.getTimestamp("started_at").toInstant()), attemptId, user.id());
        if (attempts.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz attempt not found");
        AttemptRow attempt = attempts.getFirst();
        String status = jdbc.queryForObject("SELECT status FROM quiz_attempts WHERE id = ?", String.class, attemptId);
        if (!"IN_PROGRESS".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz attempt is already closed");

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
        validateCompleteAnswers(expectedPositions, submitted.keySet());

        int correct = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (GradingRow row : rows) {
            Map<String, Object> response = submitted.getOrDefault(row.position(), Map.of());
            Map<String, Object> answer = readMap(row.answerJson());
            boolean isCorrect = submitted.containsKey(row.position()) && isCorrect(row.questionType(), response, answer);
            if (isCorrect) correct++;
            Object time = answers.stream().filter(a -> a instanceof Map<?, ?> m
                    && m.get("position") instanceof Number p && p.intValue() == row.position())
                    .map(a -> ((Map<?, ?>) a).get("timeSpentMs")).findFirst().orElse(null);
            jdbc.update("""
                INSERT INTO quiz_attempt_answers(attempt_id,position,response_json,is_correct,marks_awarded,time_spent_ms,answered_at)
                VALUES (?,?,?::jsonb,?,?,?,?)
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
        return Map.of("attemptId", attemptId, "difficulty", attempt.difficulty(),
                "questionCount", total, "correctCount", correct, "scorePercent", score,
                "submittedAt", submittedAt.toString(), "answers", results);
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
                }, user.id(), JIA_ACADEMY, level);
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
            }, user.id(), JIA_ACADEMY);
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
        }, user.id(), JIA_ACADEMY, difficulty, difficulty, limit);
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

    private Filters filters(String difficulty, String technologySlug, String categorySlug) {
        if (!LEVELS.contains(difficulty)) throw badRequest("Invalid difficulty");
        UUID technologyId = null;
        UUID categoryId = null;
        if (technologySlug != null) {
            var ids = jdbc.query("SELECT id FROM technologies WHERE academy_id=? AND slug=? AND status='PUBLISHED'",
                    (rs,n) -> rs.getObject("id", UUID.class), JIA_ACADEMY, technologySlug);
            if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Technology not found");
            technologyId = ids.getFirst();
        }
        if (categorySlug != null) {
            var rows = jdbc.query("""
                SELECT c.id,c.technology_id FROM categories c JOIN technologies t ON t.id=c.technology_id
                WHERE c.slug=? AND c.status='PUBLISHED' AND t.academy_id=?
                """, (rs,n) -> new UUID[]{rs.getObject("id", UUID.class), rs.getObject("technology_id", UUID.class)},
                    categorySlug, JIA_ACADEMY);
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
    private Object[] countArgs(Filters f) {
        List<Object> args = new ArrayList<>(List.of(JIA_ACADEMY, JIA_ACADEMY, f.difficulty()));
        if (f.technologyId() != null) args.add(f.technologyId());
        if (f.categoryId() != null) args.add(f.categoryId());
        return args.toArray();
    }
    private String questionCountSql(Filters f) {
        return "SELECT COUNT(*) FROM questions q JOIN technologies t ON t.id=q.technology_id AND t.academy_id=? "
                + "JOIN question_revisions qr ON qr.question_id=q.id AND qr.revision_number=q.current_revision "
                + "WHERE q.academy_id=? AND q.status='PUBLISHED' AND q.difficulty=? " + filterSql(f);
    }
    private Object[] startArgs(Filters f, int count) {
        List<Object> args = new ArrayList<>(List.of(JIA_ACADEMY, JIA_ACADEMY, f.difficulty()));
        if (f.technologyId() != null) args.add(f.technologyId());
        if (f.categoryId() != null) args.add(f.categoryId());
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
        Object expectedValue = answer.containsKey("correctValues") ? answer.get("correctValues") : answer.get("correct_values");
        if (!(expectedValue instanceof List<?> expected)) return false;
        Object actualValue = response.get("values");
        if (actualValue == null && response.containsKey("value")) actualValue = List.of(response.get("value"));
        if (!(actualValue instanceof List<?> actual)) return false;
        List<String> e = expected.stream().map(String::valueOf).sorted().toList();
        List<String> a = actual.stream().map(String::valueOf).sorted().toList();
        if ("MULTI_SELECT".equals(type)) return e.equals(a);
        return e.size() == 1 && a.size() == 1 && e.equals(a);
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
    private record AttemptRow(UUID id, String difficulty, Instant startedAt) {}
    private record GradingRow(int position, String questionType, String answerJson, String explanation) {}
}
