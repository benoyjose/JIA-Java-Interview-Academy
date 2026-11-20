package com.jia.academy.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jia.academy.content.service.TaxonomyAdminService;
import com.jia.academy.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class TaxonomyAcademyIsolationPostgresIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.7-alpine")
                    .withDatabaseName("jia_taxonomy_scope_test")
                    .withUsername("jia")
                    .withPassword("jia_test_only");

    private static JdbcTemplate jdbc;
    private static TaxonomyAdminService taxonomy;
    private static UUID secondAcademyId;
    private static UUID secondTechnologyId;
    private static UUID secondCategoryId;
    private static CurrentUser secondAcademyAdmin;

    @BeforeAll
    static void migrateAndSeed() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        taxonomy = new TaxonomyAdminService(jdbc);

        secondAcademyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO academies(id,code,name,status)
                VALUES (?,'SEC','Secure Engineering Academy','ACTIVE')
                """, secondAcademyId);
        secondTechnologyId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO technologies(id,academy_id,slug,name,sort_order,status)
                VALUES (?,?,'core-java','Secure Academy Java',1,'PUBLISHED')
                """, secondTechnologyId, secondAcademyId);
        secondCategoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO categories(id,technology_id,slug,name,sort_order,status)
                VALUES (?,?,'secure-java-basics','Secure Java Basics',1,'PUBLISHED')
                """, secondCategoryId, secondTechnologyId);

        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users(id,name,email,password_hash,status)
                VALUES (?,'Scoped Admin','scoped-admin@example.test','test-hash','ACTIVE')
                """, userId);
        jdbc.update("""
                INSERT INTO academy_memberships(user_id,academy_id,status)
                VALUES (?,?,'ACTIVE')
                """, userId, secondAcademyId);
        secondAcademyAdmin = new CurrentUser(userId, "Scoped Admin", "scoped-admin@example.test",
                List.of("ADMIN"), List.of("content.create"), List.of(secondAcademyId));
    }

    @Test
    void taxonomyReadsAndMutationsAreRestrictedToTheActiveAcademyMembership() {
        var technologies = taxonomy.technologies(secondAcademyAdmin);
        assertThat(technologies).extracting(TaxonomyAdminService.TechnologyItem::id)
                .containsExactly(secondTechnologyId);

        var categories = taxonomy.categories(null, secondAcademyAdmin);
        assertThat(categories).extracting(TaxonomyAdminService.CategoryItem::id).containsExactly(secondCategoryId);
        assertThat(categories).allSatisfy(category -> assertThat(category.technologyId()).isEqualTo(secondTechnologyId));

        UUID jiaTechnologyId = jdbc.queryForObject(
                "SELECT id FROM technologies WHERE academy_id=(SELECT id FROM academies WHERE code='JIA') LIMIT 1",
                UUID.class);
        assertThatThrownBy(() -> taxonomy.updateTechnology(secondAcademyAdmin, jiaTechnologyId,
                new TaxonomyAdminService.TechnologyRequest("core-java", "Attempted cross-tenant update", null, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));

        var created = taxonomy.createTechnology(secondAcademyAdmin,
                new TaxonomyAdminService.TechnologyRequest("distributed-systems", "Distributed Systems", null, 2));
        assertThat(jdbc.queryForObject("SELECT academy_id FROM technologies WHERE id=?",
                UUID.class, created.id())).isEqualTo(secondAcademyId);

        assertThatThrownBy(() -> taxonomy.create(secondAcademyAdmin,
                new TaxonomyAdminService.CategoryRequest("spring-boot", null, "cross-tenant", "Cross tenant", 1)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
    }
}
