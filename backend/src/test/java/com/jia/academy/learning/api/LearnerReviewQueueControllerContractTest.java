package com.jia.academy.learning.api;
import static org.junit.jupiter.api.Assertions.*;import org.junit.jupiter.api.Test;
class LearnerReviewQueueControllerContractTest {
 @Test void limitContractRejectsOutsideBounds(){assertThrows(IllegalArgumentException.class,()->new com.jia.academy.learning.service.AdaptiveReviewScheduler().schedule(java.util.List.of(),java.time.Instant.now(),0));assertThrows(IllegalArgumentException.class,()->new com.jia.academy.learning.service.AdaptiveReviewScheduler().schedule(java.util.List.of(),java.time.Instant.now(),101));}
}
