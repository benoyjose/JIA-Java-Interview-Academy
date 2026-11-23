package com.jia.academy.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestCorrelationFilterTest {
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void acceptsSafeIncomingIdAndClearsMdcAfterRequest() throws Exception {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var chain = mock(FilterChain.class);
        when(request.getHeader(RequestCorrelationFilter.HEADER)).thenReturn("trace-123");

        doAnswer(invocation -> {
            assertEquals("trace-123", MDC.get(RequestCorrelationFilter.MDC_KEY));
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        verify(response).setHeader(RequestCorrelationFilter.HEADER, "trace-123");
        assertNull(MDC.get(RequestCorrelationFilter.MDC_KEY));
    }

    @Test
    void replacesInvalidIncomingId() throws Exception {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var chain = mock(FilterChain.class);
        when(request.getHeader(RequestCorrelationFilter.HEADER)).thenReturn("bad id\r\nX-Injected: yes");

        filter.doFilter(request, response, chain);

        verify(response).setHeader(eq(RequestCorrelationFilter.HEADER), argThat(id -> id.matches("[0-9a-f-]{36}")));
    }
}
