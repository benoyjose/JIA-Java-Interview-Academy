package com.jia.academy.appregistry.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jia.academy.appregistry.service.AcademyRequestContext;
import com.jia.academy.appregistry.service.AcademyRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AcademyAppContextInterceptorTest {
    private static final UUID ACADEMY = UUID.fromString("50000000-0000-0000-0000-000000000001");

    @Test
    void leavesLegacyHeaderlessReadRequestsUnchanged() {
        AcademyRequestContextResolver resolver = mock(AcademyRequestContextResolver.class);
        when(resolver.resolve(null, null, null)).thenReturn(null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/technologies");
        when(request.getMethod()).thenReturn("GET");

        assertTrue(new AcademyAppContextInterceptor(resolver)
                .preHandle(request, mock(HttpServletResponse.class), new Object()));
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void rejectsHeaderlessAdminRequestsWithActionableBadRequest() {
        AcademyRequestContextResolver resolver = mock(AcademyRequestContextResolver.class);
        when(resolver.resolve(null, null, null)).thenReturn(null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/admin/questions/123/status");
        when(request.getMethod()).thenReturn("PATCH");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new AcademyAppContextInterceptor(resolver)
                        .preHandle(request, mock(HttpServletResponse.class), new Object()));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("Admin requests require registered academy-app identity"));
    }

    @Test
    void rejectsHeaderlessAdminReadToPreventUnscopedTenantContext() {
        AcademyRequestContextResolver resolver = mock(AcademyRequestContextResolver.class);
        when(resolver.resolve(null, null, null)).thenReturn(null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/admin/questions");
        when(request.getMethod()).thenReturn("GET");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new AcademyAppContextInterceptor(resolver)
                        .preHandle(request, mock(HttpServletResponse.class), new Object()));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("registered academy-app identity"));
    }

    @Test
    void rejectsClientSuppliedAcademyThatConflictsWithRegisteredApp() {
        AcademyRequestContextResolver resolver = mock(AcademyRequestContextResolver.class);
        when(resolver.resolve("JIA", "ANDROID", "com.example.jia"))
                .thenReturn(new AcademyRequestContext("JIA", ACADEMY, "ANDROID", "com.example.jia"));
        HttpServletRequest request = registeredRequest("PATCH", "/api/v1/admin/questions/1/status");
        when(request.getHeader("X-Academy-Id")).thenReturn("50000000-0000-0000-0000-000000000099");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new AcademyAppContextInterceptor(resolver)
                        .preHandle(request, mock(HttpServletResponse.class), new Object()));

        assertEquals(403, exception.getStatusCode().value());
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void storesOnlyServerResolvedAcademyContext() {
        AcademyRequestContextResolver resolver = mock(AcademyRequestContextResolver.class);
        AcademyRequestContext context =
                new AcademyRequestContext("JIA", ACADEMY, "ANDROID", "com.example.jia");
        when(resolver.resolve("JIA", "ANDROID", "com.example.jia")).thenReturn(context);
        HttpServletRequest request = registeredRequest("POST", "/api/v1/admin/questions");

        assertTrue(new AcademyAppContextInterceptor(resolver)
                .preHandle(request, mock(HttpServletResponse.class), new Object()));
        verify(request).setAttribute(AcademyAppContextInterceptor.REQUEST_CONTEXT_ATTRIBUTE, context);
    }

    @Test
    void doesNotRequireAppContextForNonAdminWritesYet() {
        AcademyRequestContextResolver resolver = mock(AcademyRequestContextResolver.class);
        when(resolver.resolve(null, null, null)).thenReturn(null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/password/reset");
        when(request.getMethod()).thenReturn("POST");

        assertTrue(new AcademyAppContextInterceptor(resolver)
                .preHandle(request, mock(HttpServletResponse.class), new Object()));
    }

    private static HttpServletRequest registeredRequest(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(AcademyRequestContextResolver.APP_CODE_HEADER)).thenReturn("JIA");
        when(request.getHeader(AcademyRequestContextResolver.PLATFORM_HEADER)).thenReturn("ANDROID");
        when(request.getHeader(AcademyRequestContextResolver.APPLICATION_ID_HEADER))
                .thenReturn("com.example.jia");
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn(method);
        return request;
    }
}
