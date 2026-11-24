package com.jia.academy.learning.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;import java.util.List;import org.junit.jupiter.api.Test;
class AdaptiveReviewSchedulerTest {
 private final AdaptiveReviewScheduler scheduler=new AdaptiveReviewScheduler();private final Instant now=Instant.parse("2026-11-24T00:00:00Z");
 @Test void weakDueEvidenceRanksFirst(){var result=scheduler.schedule(List.of(new AdaptiveReviewScheduler.Evidence("stable",now.minusSeconds(864000),5,0,95),new AdaptiveReviewScheduler.Evidence("weak",now.minusSeconds(259200),1,2,30)),now,10);assertThat(result).extracting(AdaptiveReviewScheduler.ReviewItem::itemId).containsExactly("weak","stable");}
 @Test void futureEvidenceIsExcludedAndLimitApplied(){var result=scheduler.schedule(List.of(new AdaptiveReviewScheduler.Evidence("future",now,5,0,95),new AdaptiveReviewScheduler.Evidence("due",now.minusSeconds(1728000),0,1,20)),now,1);assertThat(result).hasSize(1).extracting(AdaptiveReviewScheduler.ReviewItem::itemId).containsExactly("due");}
 @Test void invalidLimitRejected(){assertThatThrownBy(()->scheduler.schedule(List.of(),now,101)).isInstanceOf(IllegalArgumentException.class);}
}
