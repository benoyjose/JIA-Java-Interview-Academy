package com.jia.academy.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenAuthenticationFilter tokenFilter, ObjectMapper mapper) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(response.getOutputStream(), ApiErrorResponse.body("UNAUTHENTICATED", "Authentication is required."));
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(response.getOutputStream(), ApiErrorResponse.body("FORBIDDEN", "You do not have permission to perform this action."));
                }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/password/**",
                "/api/v1/auth/federated/**",
                    "/api/v1/auth/phone/**",
                    "/api/v1/technologies",
                    "/api/v1/technologies/**",
                    "/api/v1/categories/**",
                    "/api/v1/lessons/**",
                    "/api/v1/content/releases/**",
                    "/api/v1/apps/*/release-policy",
                    "/api/v1/payments/webhooks/*",
                    "/api/v1/quizzes/availability",
                    "/actuator/health",
                    "/up"
                ).permitAll()
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
