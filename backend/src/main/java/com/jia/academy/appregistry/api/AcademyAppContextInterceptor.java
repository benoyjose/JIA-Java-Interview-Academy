package com.jia.academy.appregistry.api;

import com.jia.academy.appregistry.service.AcademyRequestContext;
import com.jia.academy.appregistry.service.AcademyRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Validates registered academy identity whenever supplied. Every admin
 * endpoint requires registered app context, including reads, so tenant context
 * cannot silently disappear from audit and authorization paths. Public and
 * legacy learner reads remain compatible during staged client rollout.
 */
@Component
public class AcademyAppContextInterceptor implements HandlerInterceptor {
    public static final String REQUEST_CONTEXT_ATTRIBUTE =
            AcademyAppContextInterceptor.class.getName() + ".context";

    private final AcademyRequestContextResolver resolver;

    public AcademyAppContextInterceptor(AcademyRequestContextResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AcademyRequestContext context = resolver.resolve(
                request.getHeader(AcademyRequestContextResolver.APP_CODE_HEADER),
                request.getHeader(AcademyRequestContextResolver.PLATFORM_HEADER),
                request.getHeader(AcademyRequestContextResolver.APPLICATION_ID_HEADER));

        if (context == null) {
            if (requiresRegisteredContext(request)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Admin requests require registered academy-app identity headers");
            }
            return true;
        }

        String suppliedAcademy = request.getHeader("X-Academy-Id");
        if (suppliedAcademy != null && !suppliedAcademy.isBlank()) {
            UUID requested;
            try {
                requested = UUID.fromString(suppliedAcademy.trim());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Academy-Id must be a UUID");
            }
            if (!context.academyId().equals(requested)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Requested academy does not match registered app context");
            }
        }
        request.setAttribute(REQUEST_CONTEXT_ATTRIBUTE, context);
        return true;
    }

    private static boolean requiresRegisteredContext(HttpServletRequest request) {
        if (request.getRequestURI() == null
                || !request.getRequestURI().startsWith("/api/v1/admin/")) {
            return false;
        }
        return true;
    }
}