package com.jia.academy.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AdaptiveRevisionPolicyTest {
    private final AdaptiveRevisionPolicy policy = new AdaptiveRevisionPolicy();
    @Test void prioritizesRemediationBeforeReviewAndAdvancement() {
        assertEquals(AdaptiveRevisionPolicy.Action.REVISIT, policy.recommend(new AdaptiveRevisionPolicy.Signal(42, 20, 5, true)).action());
        assertEquals(AdaptiveRevisionPolicy.Action.PRACTICE, policy.recommend(new AdaptiveRevisionPolicy.Signal(72, 1, 4, false)).action());
        assertEquals(AdaptiveRevisionPolicy.Action.PRACTICE, policy.recommend(new AdaptiveRevisionPolicy.Signal(95, 8, 8, false)).action());
        assertEquals(AdaptiveRevisionPolicy.Action.ADVANCE, policy.recommend(new AdaptiveRevisionPolicy.Signal(92, 1, 4, false)).action());
    }
    @Test void rejectsOutOfRangeSignals() {
        assertThrows(IllegalArgumentException.class, () -> new AdaptiveRevisionPolicy.Signal(101, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new AdaptiveRevisionPolicy.Signal(50, -1, 1, false));
    }
}
