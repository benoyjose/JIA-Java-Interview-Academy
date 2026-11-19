package com.jia.academy.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiErrorResponse {
    private ApiErrorResponse() {}

    public static Map<String, Object> body(String code, String message) {
        return body(code, message, null);
    }

    public static Map<String, Object> body(String code, String message, Map<String, List<String>> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("message", message);
        if (errors != null && !errors.isEmpty()) result.put("errors", errors);
        return result;
    }

    public static String codeFor(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 422 -> "VALIDATION_ERROR";
            case 429 -> "RATE_LIMITED";
            default -> status >= 500 ? "INTERNAL_ERROR" : "HTTP_ERROR";
        };
    }
}
