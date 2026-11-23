package com.jia.academy.discovery.api;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Anonymous, read-only search over published and publicly accessible academy content. */
@RestController
@RequestMapping("/api/v1/public/academies/{academyCode}/search")
public class PublicCatalogueSearchController {
    private final JdbcTemplate jdbc;
    public PublicCatalogueSearchController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public SearchPage search(@PathVariable String academyCode,
            @RequestParam("q") String query,
            @RequestParam(defaultValue="ALL") String type,
            @RequestParam(defaultValue="ALL") String difficulty,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        if (academyCode == null || !academyCode.matches("[A-Z][A-Z0-9_]{1,39}"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy not found");
        UUID academy = jdbc.query("SELECT id FROM academies WHERE code=? AND status='ACTIVE'",
                (rs,n)->rs.getObject(1,UUID.class),academyCode).stream().findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Academy not found"));
        String q = query == null ? "" : query.trim();
        if (q.length()<2 || q.length()>80)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Search query must be 2-80 characters");
        Set<String> types=Set.of("ALL","TECHNOLOGY","CATEGORY","LESSON","QUESTION");
        Set<String> difficulties=Set.of("ALL","BEGINNER","NOVICE","PROFESSIONAL","EXPERT");
        String kind=type.toUpperCase(Locale.ROOT), level=difficulty.toUpperCase(Locale.ROOT);
        if(!types.contains(kind)||!difficulties.contains(level))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported search filter");
        int p=Math.max(0,Math.min(page,10000)), n=Math.max(1,Math.min(size,50));
        String pattern="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String filterType=kind.equals("ALL")?"%":kind;
        String filterDifficulty=level.equals("ALL")?"%":level;
        Long total=jdbc.queryForObject("""
            SELECT count(*) FROM (
              SELECT t.slug FROM technologies t WHERE t.academy_id=? AND t.status='PUBLISHED'
                AND (?='%' OR ?='TECHNOLOGY') AND (t.name ILIKE ? ESCAPE '\\' OR t.slug ILIKE ? ESCAPE '\\')
              UNION ALL
              SELECT c.slug FROM categories c JOIN technologies t ON t.id=c.technology_id
                WHERE t.academy_id=? AND t.status='PUBLISHED' AND c.status='PUBLISHED'
                AND (?='%' OR ?='CATEGORY') AND (c.name ILIKE ? ESCAPE '\\' OR c.slug ILIKE ? ESCAPE '\\')
              UNION ALL
              SELECT l.slug FROM lessons l JOIN lesson_revisions r ON r.id=l.published_revision_id
                JOIN categories c ON c.id=l.category_id JOIN technologies t ON t.id=c.technology_id
                WHERE t.academy_id=? AND t.status='PUBLISHED' AND c.status='PUBLISHED' AND l.status='PUBLISHED'
                AND (?='%' OR ?='LESSON') AND (?='%' OR r.difficulty=?)
                AND (r.title ILIKE ? ESCAPE '\\' OR l.slug ILIKE ? ESCAPE '\\' OR COALESCE(r.summary,'') ILIKE ? ESCAPE '\\')
                AND NOT EXISTS(SELECT 1 FROM lesson_entitlement_requirements req WHERE req.lesson_id=l.id)
              UNION ALL
              SELECT q.slug FROM questions q JOIN question_revisions qr ON qr.question_id=q.id AND qr.revision_number=q.current_revision
                JOIN technologies t ON t.id=q.technology_id LEFT JOIN categories c ON c.id=q.category_id
                WHERE q.academy_id=? AND t.academy_id=? AND t.status='PUBLISHED' AND q.status='PUBLISHED'
                AND (q.category_id IS NULL OR c.status='PUBLISHED') AND (?='%' OR ?='QUESTION')
                AND (?='%' OR q.difficulty=?) AND (qr.prompt_json::text ILIKE ? ESCAPE '\\' OR COALESCE(q.slug,'') ILIKE ? ESCAPE '\\')
            ) matches
            """,Long.class,academy,filterType,kind,pattern,pattern,academy,filterType,kind,pattern,pattern,
            academy,filterType,kind,filterDifficulty,level,pattern,pattern,pattern,academy,academy,
            filterType,kind,filterDifficulty,level,pattern,pattern);
        List<SearchItem> items=jdbc.query("""
            SELECT type,slug,title,summary,category_slug FROM (
              SELECT 'TECHNOLOGY' type,t.slug,t.name title,COALESCE(t.description,'') summary,
                NULL::varchar category_slug,0 rank FROM technologies t
                WHERE t.academy_id=? AND t.status='PUBLISHED' AND (?='%' OR ?='TECHNOLOGY')
                AND (t.name ILIKE ? ESCAPE '\\' OR t.slug ILIKE ? ESCAPE '\\')
              UNION ALL
              SELECT 'CATEGORY',c.slug,c.name,'',c.slug,1 FROM categories c JOIN technologies t ON t.id=c.technology_id
                WHERE t.academy_id=? AND t.status='PUBLISHED' AND c.status='PUBLISHED' AND (?='%' OR ?='CATEGORY')
                AND (c.name ILIKE ? ESCAPE '\\' OR c.slug ILIKE ? ESCAPE '\\')
              UNION ALL
              SELECT 'LESSON',l.slug,r.title,COALESCE(r.summary,''),c.slug,2 FROM lessons l
                JOIN lesson_revisions r ON r.id=l.published_revision_id JOIN categories c ON c.id=l.category_id
                JOIN technologies t ON t.id=c.technology_id WHERE t.academy_id=? AND t.status='PUBLISHED'
                AND c.status='PUBLISHED' AND l.status='PUBLISHED' AND (?='%' OR ?='LESSON')
                AND (?='%' OR r.difficulty=?) AND (r.title ILIKE ? ESCAPE '\\' OR l.slug ILIKE ? ESCAPE '\\'
                OR COALESCE(r.summary,'') ILIKE ? ESCAPE '\\')
                AND NOT EXISTS(SELECT 1 FROM lesson_entitlement_requirements req WHERE req.lesson_id=l.id)
              UNION ALL
              SELECT 'QUESTION',q.slug,COALESCE(qr.prompt_json->>'text',qr.prompt_json->>'question','Question'),
                '',c.slug,3 FROM questions q JOIN question_revisions qr ON qr.question_id=q.id AND qr.revision_number=q.current_revision
                JOIN technologies t ON t.id=q.technology_id LEFT JOIN categories c ON c.id=q.category_id
                WHERE q.academy_id=? AND t.academy_id=? AND t.status='PUBLISHED' AND q.status='PUBLISHED'
                AND (q.category_id IS NULL OR c.status='PUBLISHED') AND (?='%' OR ?='QUESTION')
                AND (?='%' OR q.difficulty=?) AND (qr.prompt_json::text ILIKE ? ESCAPE '\\' OR COALESCE(q.slug,'') ILIKE ? ESCAPE '\\')
            ) results ORDER BY rank,title,slug LIMIT ? OFFSET ?
            """,(rs,row)->new SearchItem(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),
            academy,filterType,kind,pattern,pattern,academy,filterType,kind,pattern,pattern,
            academy,filterType,kind,filterDifficulty,level,pattern,pattern,pattern,academy,academy,
            filterType,kind,filterDifficulty,level,pattern,pattern,n,p*n);
        return new SearchPage(items,p,n,total==null?0:total,(int)Math.ceil((total==null?0:total)/(double)n));
    }
    public record SearchItem(String type,String slug,String title,String summary,String categorySlug) {}
    public record SearchPage(List<SearchItem> items,int page,int size,long totalItems,int totalPages) {}
}
