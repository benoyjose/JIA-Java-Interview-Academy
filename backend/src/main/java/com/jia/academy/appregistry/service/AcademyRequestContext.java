package com.jia.academy.appregistry.service;

import java.util.UUID;

/**
 * Server-resolved academy identity for a request. Values are never accepted
 * directly from a caller-supplied academy UUID.
 */
public record AcademyRequestContext(String appCode, UUID academyId, String platform,
        String applicationId) {
}
