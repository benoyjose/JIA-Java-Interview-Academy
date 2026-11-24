package com.jia.academy.coding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class CodingSubmissionLifecyclePolicyTest {
    private final CodingSubmissionLifecyclePolicy policy = new CodingSubmissionLifecyclePolicy();

    @Test void queuedSubmissionIsCancelledImmediately() {
        var decision = policy.cancellation("QUEUED");
        assertThat(decision.action()).isEqualTo(CodingSubmissionLifecyclePolicy.Action.CANCEL_QUEUED);
        assertThat(decision.responseStatus()).isEqualTo("CANCELLED");
        assertThat(decision.cancelRequested()).isFalse();
    }

    @Test void runningSubmissionReceivesCooperativeCancellationRequest() {
        var decision = policy.cancellation("RUNNING");
        assertThat(decision.action()).isEqualTo(CodingSubmissionLifecyclePolicy.Action.REQUEST_RUNNING_CANCELLATION);
        assertThat(decision.responseStatus()).isEqualTo("RUNNING");
        assertThat(decision.cancelRequested()).isTrue();
    }

    @Test void terminalSubmissionIsIdempotentAndUnchanged() {
        for (String status : java.util.List.of("COMPLETED", "COMPILE_ERROR", "TIME_LIMIT_EXCEEDED",
                "MEMORY_LIMIT_EXCEEDED", "CANCELLED", "INTERNAL_ERROR")) {
            var decision = policy.cancellation(status);
            assertThat(decision.action()).isEqualTo(CodingSubmissionLifecyclePolicy.Action.NO_OP_TERMINAL);
            assertThat(decision.responseStatus()).isEqualTo(status);
            assertThat(decision.cancelRequested()).isFalse();
        }
    }

    @Test void unknownStatusFailsClosed() {
        assertThatThrownBy(() -> policy.cancellation("WAITING_FOR_UNKNOWN_WORKER"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
