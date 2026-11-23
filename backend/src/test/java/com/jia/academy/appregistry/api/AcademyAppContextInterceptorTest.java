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
    void leavesLegacyHeaderlessRequestsUnchanged() {
        AcademyRequestContextResolver resolver = mock(AcademyRequestContextResolver.class);
        when(resolver.resolve(null, null, null)).thenReturn(null);
        AcademyAppContextInterceptor interceptor = new AcademyAppContextInterceptor(resolver);
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertTrue(interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()));
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void rejectsClientSuppliedAcademyThatConflictsWithRegisteredApp() {
        AcademyRequestContextResolver resolver = mock(AcademyRequestContextResolver.class);
        when(resolver.resolve("JIA", "ANDROID", "com.example.jia"))
                .thenReturn(new AcademyRequestContext("JIA", ACADEMY, "ANDROID", "com.example.jia"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(AcademyRequestContextResolver.APP_CODE_HEADER)).thenReturn("JIA");
        when(request.getHeader(AcademyRequestContextResolver.PLATFORM_HEADER)).thenReturn("ANDROID");
        when(request.getHeader(AcademyRequestContextResolver.APPLICATION_ID_HEADER)).thenReturn("com.example.jia");
        when(request.getHeader("X-Academy-Id")).thenReturn("50000000-0000-0000-0000-000000000099");

        AcademyAppContextInterceptor interceptor = new AcademyAppContextInterceptor(resolver);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> interceptor.preHandle(request, mock(HttpServletResponse.class), new Object()));

        assertEquals(403, exception.getStatusCode().value());
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void storesOnlyServerResolvedAcademyContext() {
        AcademyRequestContextResolver resolver = mock(AcademyRequestContextResolver.class);
        AcademyRequestContext context =
                new AcademyRequestContext("JIA", ACADEMY, "ANDROID", "com.example.jia");
        when(resolver.resolve("JIA", "ANDROID", "com.example.jia")).thenReturn(context);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(AcademyRequestContextResolver.APP_CODE_HEADER)).thenReturn("JIA");
        when(request.getHeader(AcademyRequestContextResolver.PLATFORM_HEADER)).thenReturn("ANDROID");
        when(request.getHeader(AcademyRequestContextResolver.APPLICATION_ID_HEADER)).thenReturn("com.example.jia");

        assertTrue(new AcademyAppContextInterceptor(resolver)
                .preHandle(request, mock(HttpServletResponse.class), new Object()));
        verify(request).setAttribute(AcademyAppContextInterceptor.REQUEST_CONTEXT_ATTRIBUTE, context);
    }
}
