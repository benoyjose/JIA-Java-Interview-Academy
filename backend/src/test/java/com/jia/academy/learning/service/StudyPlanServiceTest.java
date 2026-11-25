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

    @Test void equalPriorityTasksHaveStableLexicalOrderRegardlessOfInputOrder() {
        var later = new StudyPlanService.Task("PRACTICE_TOPIC","Zulu",null,"zulu",10,"practice",45);
        var earlier = new StudyPlanService.Task("PRACTICE_TOPIC","Alpha",null,"alpha",10,"practice",45);
        assertThat(service.allocate(List.of(later, earlier), 30))
                .extracting(StudyPlanService.Task::topicSlug).containsExactly("alpha", "zulu");
        assertThat(service.allocate(List.of(earlier, later), 30))
                .extracting(StudyPlanService.Task::topicSlug).containsExactly("alpha", "zulu");
    }

    @Test void invalidTaskDurationStillConsumesAtLeastOneMinuteAndPlanRemainsBounded() {
        var plan = service.allocate(List.of(new StudyPlanService.Task("TASK","Zero",null,null,0,"edge",10)), 10);
        assertThat(plan).hasSize(1);
        assertThat(plan.getFirst().minutes()).isEqualTo(1);
    }
}
