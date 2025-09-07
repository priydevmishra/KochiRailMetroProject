package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentWorkflow;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentWorkflowRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class WorkflowService {

    private final DocumentWorkflowRepository workflowRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public WorkflowService(DocumentWorkflowRepository workflowRepository,
                           NotificationService notificationService,
                           AuditService auditService) {
        this.workflowRepository = workflowRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    public Page<DocumentWorkflow> getMyWorkflows(UserPrincipal currentUser, Pageable pageable) {
        User user = new User();
        user.setId(currentUser.getId());
        return workflowRepository.findByAssignedTo(user, pageable);
    }

    public Page<DocumentWorkflow> getPendingWorkflows(UserPrincipal currentUser, Pageable pageable) {
        User user = new User();
        user.setId(currentUser.getId());
        List<DocumentWorkflow.WorkflowStatus> pendingStatuses = List.of(
                DocumentWorkflow.WorkflowStatus.PENDING,
                DocumentWorkflow.WorkflowStatus.IN_PROGRESS
        );
        return workflowRepository.findByAssignedToAndStatusIn(user, pendingStatuses, pageable);
    }

    public DocumentWorkflow updateWorkflowStatus(Long workflowId,
                                                 DocumentWorkflow.WorkflowStatus newStatus,
                                                 String comments,
                                                 UserPrincipal currentUser) {
        DocumentWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));

        DocumentWorkflow.WorkflowStatus oldStatus = workflow.getCurrentStatus();
        workflow.setCurrentStatus(newStatus);
        workflow.setComments(comments);
        workflow.setUpdatedAt(LocalDateTime.now());

        workflow = workflowRepository.save(workflow);

        // Log audit
        auditService.logAction(currentUser.getId(), workflow.getDocument().getId(),
                "WORKFLOW_STATUS_UPDATED",
                "Status changed from " + oldStatus + " to " + newStatus + ". Comments: " + comments);

        // Send notification if workflow is completed or requires attention
        if (newStatus == DocumentWorkflow.WorkflowStatus.COMPLETED ||
                newStatus == DocumentWorkflow.WorkflowStatus.APPROVED) {
            // Notify document owner or relevant parties
            // Implementation depends on your notification requirements
        }

        return workflow;
    }

    public List<DocumentWorkflow> getUpcomingDeadlines(int daysAhead) {
        LocalDateTime deadline = LocalDateTime.now().plusDays(daysAhead);
        return workflowRepository.findUpcomingDeadlines(deadline);
    }

    public List<DocumentWorkflow> getOverdueWorkflows() {
        return workflowRepository.findOverdueWorkflows(LocalDateTime.now());
    }

    @Transactional
    public void processDeadlineReminders() {
        // Send reminders for workflows due in 24 hours
        List<DocumentWorkflow> upcomingDeadlines = getUpcomingDeadlines(1);

        for (DocumentWorkflow workflow : upcomingDeadlines) {
            notificationService.sendDeadlineReminder(workflow);
        }

        // Mark overdue workflows
        List<DocumentWorkflow> overdueWorkflows = getOverdueWorkflows();
        for (DocumentWorkflow workflow : overdueWorkflows) {
            if (workflow.getCurrentStatus() == DocumentWorkflow.WorkflowStatus.PENDING) {
                workflow.setCurrentStatus(DocumentWorkflow.WorkflowStatus.EXPIRED);
                workflowRepository.save(workflow);

                auditService.logAction(null, workflow.getDocument().getId(),
                        "WORKFLOW_EXPIRED", "Workflow expired due to missed deadline");
            }
        }
    }
}
