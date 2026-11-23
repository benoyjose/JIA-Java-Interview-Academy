package com.jia.academy.learning.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StudyPlanService {
    public record Task(String kind, String title, String lessonSlug, String topicSlug,
                      int minutes, String reason, int priority) {}
    public List<Task> allocate(List<Task> candidates, int dailyMinutes) {
        int budget = Math.max(10, Math.min(180, dailyMinutes));
        List<Task> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(Task::priority).reversed());
        List<Task> plan = new ArrayList<>();
        for (Task task : sorted) {
            if (budget <= 0) break;
            int allotted = Math.min(task.minutes(), budget);
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
