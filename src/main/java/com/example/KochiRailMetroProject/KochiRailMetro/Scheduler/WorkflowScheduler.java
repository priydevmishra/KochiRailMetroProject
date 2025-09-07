package com.example.KochiRailMetroProject.KochiRailMetro.Scheduler;

import com.example.KochiRailMetroProject.KochiRailMetro.Service.WorkflowService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkflowScheduler {

    private final WorkflowService workflowService;

    public WorkflowScheduler(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    // Run every hour to check deadlines
    @Scheduled(cron = "0 0 * * * *")
    public void checkWorkflowDeadlines() {
        workflowService.processDeadlineReminders();
    }
}
