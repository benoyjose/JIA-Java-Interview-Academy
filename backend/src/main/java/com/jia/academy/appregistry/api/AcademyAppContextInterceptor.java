package com.jia.academy.appregistry.api;

import com.jia.academy.appregistry.service.AcademyRequestContext;
import com.jia.academy.appregistry.service.AcademyRequestContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Staged identity enforcement: validates academy-app headers whenever supplied.
 * Routes retain headerless JIA compatibility during registered app rollout.
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
        if (context == null) return true;

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
}
