package com.jia.academy.content.service;

import com.jia.academy.security.CurrentUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaxonomyAdminService {
    private final JdbcTemplate jdbc;

    public TaxonomyAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    @Transactional
    public TechnologyItem createTechnology(CurrentUser actor, TechnologyRequest request) {
        validateTechnologyRequest(request);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO technologies(id,slug,name,description,sort_order,status,created_at,updated_at)
                VALUES (?,?,?,?,?,'DRAFT',now(),now())
                """, id, request.slug().trim().toLowerCase(Locale.ROOT), request.name().trim(),
                request.description(), request.sortOrder());
        audit(actor, "TAXONOMY_TECHNOLOGY_CREATED", "TECHNOLOGY", id);
        return technologies().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public TechnologyItem updateTechnology(CurrentUser actor, UUID id, TechnologyRequest request) {
        validateTechnologyRequest(request);
        TechnologyItem current = technologies().stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technology not found"));
        jdbc.update("""
                UPDATE technologies SET slug=?,name=?,description=?,sort_order=?,updated_at=now() WHERE id=?
                """, request.slug().trim().toLowerCase(Locale.ROOT), request.name().trim(),
                request.description(), request.sortOrder(), id);
        audit(actor, "TAXONOMY_TECHNOLOGY_UPDATED", "TECHNOLOGY", id);
        return technologies().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void archiveTechnology(CurrentUser actor, UUID id) {
        TechnologyItem current = technologies().stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technology not found"));
        Integer categories = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE technology_id=?",
                Integer.class, id);
        if (categories != null && categories > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Archive or move all categories before archiving this technology");
        }
        jdbc.update("UPDATE technologies SET status='ARCHIVED',updated_at=now() WHERE id=?", id);
        audit(actor, "TAXONOMY_TECHNOLOGY_ARCHIVED", "TECHNOLOGY", id);
    }

    public List<TechnologyItem> technologies() {
        return jdbc.query("""
                SELECT id, slug, name, description, sort_order, status
                FROM technologies ORDER BY sort_order, name
                """, (rs, row) -> new TechnologyItem(
                rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                rs.getString("description"), rs.getInt("sort_order"), rs.getString("status")));
    }

    @Transactional(readOnly = true)
    public List<CategoryItem> categories(String technologySlug) {
        return jdbc.query("""
                SELECT c.id, c.technology_id, t.slug AS technology_slug, c.parent_id,
                       p.slug AS parent_slug, c.slug, c.name, c.sort_order, c.status,
                       (SELECT COUNT(*) FROM lessons l WHERE l.category_id=c.id) AS lesson_count,
                       (SELECT COUNT(*) FROM questions q WHERE q.category_id=c.id) AS question_count
                FROM categories c
                JOIN technologies t ON t.id=c.technology_id
                LEFT JOIN categories p ON p.id=c.parent_id
                WHERE (? IS NULL OR t.slug=?)
                ORDER BY t.sort_order, c.sort_order, c.name
                """, (rs, row) -> new CategoryItem(
                rs.getObject("id", UUID.class), rs.getObject("technology_id", UUID.class),
                rs.getString("technology_slug"), rs.getObject("parent_id", UUID.class),
                rs.getString("parent_slug"), rs.getString("slug"), rs.getString("name"),
                rs.getInt("sort_order"), rs.getString("status"),
                rs.getLong("lesson_count"), rs.getLong("question_count")),
                technologySlug, technologySlug);
    }

    @Transactional
    public CategoryItem create(CurrentUser actor, CategoryRequest request) {
        validateRequest(request);
        UUID technologyId = technologyId(request.technologySlug());
        UUID parentId = validateParent(request.parentId(), technologyId, null);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO categories(id,technology_id,parent_id,slug,name,sort_order,status)
                    VALUES (?,?,?,?,?,?,'DRAFT')
                    """, id, technologyId, parentId, normalizeSlug(request.slug()),
                    request.name().trim(), request.sortOrder());
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this slug already exists");
        }
        audit(actor, "TAXONOMY_CATEGORY_CREATED", id);
        return categories(null).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public CategoryItem update(CurrentUser actor, UUID id, CategoryRequest request) {
        validateRequest(request);
        CategoryItem current = categories(null).stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if ("ARCHIVED".equals(current.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived categories cannot be edited");
        }
        UUID technologyId = technologyId(request.technologySlug());
        if (!current.technologyId().equals(technologyId)) {
            Integer children = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE parent_id=?", Integer.class, id);
            if (children != null && children > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Move child categories before moving a parent to another technology");
            }
        }
        UUID parentId = validateParent(request.parentId(), technologyId, id);
        try {
            int changed = jdbc.update("""
                    UPDATE categories SET technology_id=?,parent_id=?,slug=?,name=?,sort_order=?,updated_at=now()
                    WHERE id=? AND status<>'ARCHIVED'
                    """, technologyId, parentId, normalizeSlug(request.slug()), request.name().trim(),
                    request.sortOrder(), id);
            if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this slug already exists");
        }
        audit(actor, "TAXONOMY_CATEGORY_UPDATED", id);
        return categories(null).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void archive(CurrentUser actor, UUID id) {
        CategoryItem category = categories(null).stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        Integer children = jdbc.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE parent_id=? AND status<>'ARCHIVED'", Integer.class, id);
        if (children != null && children > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Move or archive child categories first");
        }
        if (category.lessonCount() > 0 || category.questionCount() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Categories containing lessons or questions cannot be archived; move content first");
        }
        jdbc.update("UPDATE categories SET status='ARCHIVED',updated_at=now() WHERE id=?", id);
        audit(actor, "TAXONOMY_CATEGORY_ARCHIVED", id);
    }

    private static void validateTechnologyRequest(TechnologyRequest request) {
        if (request == null || request.slug() == null || !request.slug().matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                || request.slug().length() > 80 || request.name() == null || request.name().isBlank()
                || request.name().trim().length() > 140 || request.sortOrder() < 0
                || (request.description() != null && request.description().length() > 500)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid technology payload");
        }
    }

    private static void validateRequest(CategoryRequest request) {
        if (request == null || request.technologySlug() == null || request.technologySlug().isBlank()
                || request.slug() == null || request.name() == null || request.name().isBlank()
                || request.name().trim().length() > 160 || request.sortOrder() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Technology, slug, name and non-negative sort order are required");
        }
    }

    private UUID technologyId(String slug) {
        return jdbc.query("SELECT id FROM technologies WHERE slug=? AND status<>'ARCHIVED'",
                (rs, row) -> rs.getObject(1, UUID.class), slug.trim())
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Technology not found"));
    }

    private UUID validateParent(UUID parentId, UUID technologyId, UUID selfId) {
        if (parentId == null) return null;
        if (parentId.equals(selfId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A category cannot be its own parent");
        }
        var parents = jdbc.query("""
                SELECT technology_id FROM categories WHERE id=? AND status<>'ARCHIVED'
                """, (rs, row) -> rs.getObject(1, UUID.class), parentId);
        if (parents.isEmpty() || !technologyId.equals(parents.getFirst())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Parent category must exist under the selected technology");
        }
        if (selfId != null) {
            Integer cycle = jdbc.queryForObject("""
                    WITH RECURSIVE descendants(id) AS (
                      SELECT id FROM categories WHERE parent_id=?
                      UNION ALL SELECT c.id FROM categories c JOIN descendants d ON c.parent_id=d.id
                    ) SELECT COUNT(*) FROM descendants WHERE id=?
                    """, Integer.class, selfId, parentId);
            if (cycle != null && cycle > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category hierarchy cannot contain cycles");
            }
        }
        return parentId;
    }

    private static String normalizeSlug(String value) {
        String slug = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Slug must contain lowercase letters, numbers and single hyphens");
        }
        return slug;
    }

    private void audit(CurrentUser actor, String action, UUID id) {
        jdbc.update("""
                INSERT INTO audit_events(id,actor_user_id,action,entity_type,entity_id,metadata_json,occurred_at)
                VALUES (?,?,?,'CATEGORY',?,jsonb_build_object('source','admin-taxonomy'),?)
                """, UUID.randomUUID(), actor.id(), action, id, Timestamp.from(Instant.now()));
    }

    public record TechnologyItem(UUID id, String slug, String name, String description,
                                 int sortOrder, String status) {}
    public record CategoryItem(UUID id, UUID technologyId, String technologySlug, UUID parentId,
                               String parentSlug, String slug, String name, int sortOrder,
                               String status, long lessonCount, long questionCount) {}
    public record CategoryRequest(String technologySlug, UUID parentId, String slug,
                                  String name, int sortOrder) {}
}
