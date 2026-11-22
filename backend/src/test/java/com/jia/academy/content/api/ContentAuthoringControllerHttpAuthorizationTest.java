package com.jia.academy.content.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jia.academy.content.service.ContentAuthoringService;
import com.jia.academy.content.service.LessonImportService;
import com.jia.academy.content.service.LessonRevisionHistoryService;
import com.jia.academy.security.SecurityConfiguration;
import com.jia.academy.security.TokenAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mockito;

@WebMvcTest
@Import({ContentAuthoringController.class, SecurityConfiguration.class, TokenAuthenticationFilter.class, ContentAuthoringControllerHttpAuthorizationTest.MockBeans.class})
class ContentAuthoringControllerHttpAuthorizationTest {
    @Autowired MockMvc mvc;

    @Test
    void editorPermissionMatrixUsesActualContentRoutes() throws Exception {
        mvc.perform(get("/api/v1/admin/lessons").with(user("learner")
                .authorities(new SimpleGrantedAuthority("catalogue.read"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/lessons").with(user("reviewer")
                .authorities(new SimpleGrantedAuthority("content.review"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/lessons/" + java.util.UUID.randomUUID() + "/submit")
                .with(user("reviewer").authorities(new SimpleGrantedAuthority("content.review"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewerAndPublisherPermissionMatrixUsesActualContentRoutes() throws Exception {
        mvc.perform(get("/api/v1/admin/reviews").with(user("editor")
                .authorities(new SimpleGrantedAuthority("content.create"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/lessons/" + java.util.UUID.randomUUID() + "/publish")
                .with(user("reviewer").authorities(new SimpleGrantedAuthority("content.review"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/lessons/" + java.util.UUID.randomUUID() + "/archive")
                .with(user("editor").authorities(new SimpleGrantedAuthority("content.create"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignedContentPermissionPassesAuthorizationOnActualRoute() throws Exception {
        mvc.perform(get("/api/v1/admin/lessons").with(user("editor")
                .authorities(new SimpleGrantedAuthority("content.create"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/reviews").with(user("reviewer")
                .authorities(new SimpleGrantedAuthority("content.review"))))
                .andExpect(status().isOk());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class MockBeans {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean JdbcTemplate jdbcTemplate() { return Mockito.mock(JdbcTemplate.class); }
        @Bean ContentAuthoringService contentAuthoringService() { return Mockito.mock(ContentAuthoringService.class); }
        @Bean LessonImportService lessonImportService() { return Mockito.mock(LessonImportService.class); }
        @Bean LessonRevisionHistoryService lessonRevisionHistoryService() { return Mockito.mock(LessonRevisionHistoryService.class); }
    }
}
