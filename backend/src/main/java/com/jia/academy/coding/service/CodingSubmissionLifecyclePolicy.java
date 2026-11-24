package com.jia.academy.coding.service;

/**
 * Fail-closed lifecycle policy for queued coding submissions.
 * It only controls queue state; submitted source is never executed here.
 */
public final class CodingSubmissionLifecyclePolicy {
    public enum Action { CANCEL_QUEUED, REQUEST_RUNNING_CANCELLATION, NO_OP_TERMINAL }
    public record Decision(Action action, String responseStatus, boolean cancelRequested) {}

    public Decision cancellation(String status) {
        if (status == null) throw new IllegalArgumentException("status is required");
        return switch (status) {
            case "QUEUED" -> new Decision(Action.CANCEL_QUEUED, "CANCELLED", false);
            case "RUNNING" -> new Decision(Action.REQUEST_RUNNING_CANCELLATION, "RUNNING", true);
            case "COMPLETED", "COMPILE_ERROR", "TIME_LIMIT_EXCEEDED",
                    "MEMORY_LIMIT_EXCEEDED", "CANCELLED", "INTERNAL_ERROR" ->
                    new Decision(Action.NO_OP_TERMINAL, status, false);
            default -> throw new IllegalArgumentException("Unsupported submission status: " + status);
        };
    }
}
