package com.jia.academy.security;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class ApiErrorHttpIntegrationTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ErrorTestController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }


    @Test
    void returnsSuccessfulJsonResponseWithoutBackendSpecificEnvelope() throws Exception {
        mvc.perform(get("/api/v1/__test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void returnsContractShapeForMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/__test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request body is invalid."));
    }

    @Test
    void returnsFieldErrorsForValidationFailures() throws Exception {
        mvc.perform(post("/api/v1/__test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("The request contains invalid fields."))
                .andExpect(jsonPath("$.errors.name").isArray());
    }

    @Test
    void returnsNotFoundContractForMissingResources() throws Exception {
        mvc.perform(get("/api/v1/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void returnsForbiddenContractForDeniedOperations() throws Exception {
        mvc.perform(get("/api/v1/__test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mvc.perform(get("/api/v1/__test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred."))
                .andExpect(content().string(not(containsString("database password"))));
    }

    @RestController
    @RequestMapping("/api/v1/__test")
    static class ErrorTestController {
        @PostMapping("/validation")
        String validation(@Valid @RequestBody ValidationRequest request) {
            return "ok";
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Private lookup detail");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("Private permission detail");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("database password must never leak");
        }
    }

    record ValidationRequest(@NotBlank String name) {}
}
