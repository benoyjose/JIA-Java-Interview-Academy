package com.jia.academy.learning.api;

import com.jia.academy.appregistry.api.AcademyAppContextInterceptor;
import com.jia.academy.appregistry.service.AcademyRequestContext;
import com.jia.academy.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the authenticated learner's registered academy; never silently selects a global tenant. */
final class LearnerAcademyContext {
    private LearnerAcademyContext() {}

    static UUID resolve(CurrentUser user, HttpServletRequest request) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        AcademyRequestContext context = request == null ? null :
            (AcademyRequestContext) request.getAttribute(AcademyAppContextInterceptor.REQUEST_CONTEXT_ATTRIBUTE);
        if (context != null) {
            UUID academyId = context.academyId();
            if (user.academyIds() == null || !user.academyIds().contains(academyId))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Learner is not a member of the registered academy");
            return academyId;
        }
        if (user.academyIds() != null && user.academyIds().size() == 1)
            return user.academyIds().get(0);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Registered academy-app context is required for multi-academy learner operations");
    }
}
