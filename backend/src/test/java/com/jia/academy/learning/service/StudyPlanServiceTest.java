package com.jia.academy.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudyPlanServiceTest {
    private final StudyPlanService service = new StudyPlanService();
    @Test void weakTopicsRankAheadOfLessonsAndMasteredTopicsAreExcludedByPolicy() {
        assertThat(StudyPlanService.topicPriority(28)).isGreaterThan(StudyPlanService.topicPriority(76));
        assertThat(StudyPlanService.topicPriority(100)).isEqualTo(1);
        assertThat(StudyPlanService.topicPriority(null)).isEqualTo(40);
    }
    @Test void planNeverExceedsRequestedBudgetAndTruncatesFinalTask() {
        var tasks = List.of(new StudyPlanService.Task("TOPIC","Concurrency",null,"concurrency",10,"weak",72),
            new StudyPlanService.Task("LESSON","Streams","streams",null,15,"resume",30));
        var plan = service.allocate(tasks, 20);
        assertThat(plan).hasSize(2);
        assertThat(plan.stream().mapToInt(StudyPlanService.Task::minutes).sum()).isEqualTo(20);
        assertThat(plan.getLast().minutes()).isEqualTo(10);
    }
}
