package com.jia.academy.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.mockito.Mockito;

@WebMvcTest(controllers = SecurityConfigurationHttpIntegrationTest.ProtectedTestController.class)
@Import({
        SecurityConfiguration.class,
        TokenAuthenticationFilter.class,
        SecurityConfigurationHttpIntegrationTest.MockJdbcConfiguration.class
})
class SecurityConfigurationHttpIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void missingBearerTokenIsRejectedByTheProductionSecurityFilterChain() throws Exception {
        mvc.perform(get("/api/v1/__test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void invalidBearerTokenIsRejectedByTheProductionSecurityFilterChain() throws Exception {
        mvc.perform(get("/api/v1/__test/protected")
                        .header("Authorization", "Bearer invalid-test-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @RestController
    @RequestMapping("/api/v1/__test")
    static class ProtectedTestController {
        @GetMapping("/protected")
        String protectedResource() {
            return "protected";
        }
    }

    @TestConfiguration
    static class MockJdbcConfiguration {
        @Bean
        JdbcTemplate jdbcTemplate() {
            return Mockito.mock(JdbcTemplate.class);
        }
    }
}
