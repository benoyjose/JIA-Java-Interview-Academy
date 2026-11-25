package com.jia.academy.learning.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StudyPlanService {
    public record Task(String kind, String title, String lessonSlug, String topicSlug,
                      int minutes, String reason, int priority) {}

    private static final Comparator<Task> PLAN_ORDER =
            Comparator.comparingInt(Task::priority).reversed()
                    .thenComparing(Task::kind, Comparator.nullsLast(String::compareTo))
                    .thenComparing(Task::topicSlug, Comparator.nullsLast(String::compareTo))
                    .thenComparing(Task::lessonSlug, Comparator.nullsLast(String::compareTo))
                    .thenComparing(Task::title, Comparator.nullsLast(String::compareTo));

    /** Allocate a bounded daily plan with a stable order for equal-priority candidates. */
    public List<Task> allocate(List<Task> candidates, int dailyMinutes) {
        int budget = Math.max(10, Math.min(180, dailyMinutes));
        List<Task> sorted = new ArrayList<>(candidates);
        sorted.sort(PLAN_ORDER);
        List<Task> plan = new ArrayList<>();
        for (Task task : sorted) {
            if (budget <= 0) break;
            int allotted = Math.min(Math.max(1, task.minutes()), budget);
            plan.add(new Task(task.kind(), task.title(), task.lessonSlug(), task.topicSlug(),
                    allotted, task.reason(), task.priority()));
            budget -= allotted;
        }
        return List.copyOf(plan);
    }

    public static int topicPriority(Integer accuracyPercent) {
        return accuracyPercent == null ? 40 : Math.max(1, 100 - Math.max(0, Math.min(100, accuracyPercent)));
    }
}
