package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentBasicDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentContentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.UserBasicDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.WorkflowResponseDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.*;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.*;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.Hibernate;   // 🔴 Added import
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class WorkflowService {
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentWorkflowRepository workflowRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public WorkflowService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           DocumentWorkflowRepository workflowRepository,
                           NotificationService notificationService,
                           AuditService auditService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.workflowRepository = workflowRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // Admin -> auto assign to manager of document.department
    @Transactional
    public WorkflowResponseDto assignToDepartmentManager(Long documentId,
                                                         UserPrincipal currentUser) {

        // 🔹 Fetch document
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Department department = document.getDepartment();
        if (department == null) {
            throw new RuntimeException("Document has no department assigned");
        }

        // 🔹 Fetch manager
        User manager = userRepository.findFirstManagerByDepartmentCode(department.getCode())
                .orElseThrow(() -> new RuntimeException("No manager found for department " + department.getCode()));

        // 🔹 Fetch admin (current user)
        User admin = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        // 🔹 Create workflow
        DocumentWorkflow workflow = new DocumentWorkflow();
        workflow.setDocument(document);
        workflow.setCreatedBy(admin);
        workflow.setAssignedTo(manager);
        workflow.setCurrentStatus(DocumentWorkflow.WorkflowStatus.PENDING);

        // 🔹 Use deadline from DocumentContent if exists
        DocumentContent content = document.getContent();
        if (content != null && content.getDeadline() != null) {
            workflow.setDeadline(content.getDeadline());
        }

        workflow.setCreatedAt(LocalDateTime.now());
        workflowRepository.save(workflow);

        auditService.logAction(admin.getId(), documentId, "TASK_ASSIGNED",
                "Admin auto-assigned to manager " + manager.getFullName() + " for dept " + department.getCode());

        notificationService.sendTaskNotification(workflow);

        // 🔹 Build DTOs for response (convert LocalDateTime to String)
        DocumentContentDto contentDto = null;
        if (content != null) {
            contentDto = new DocumentContentDto(
                    content.getExtractedText(),
                    content.getSummary(),
                    content.getMlSummary(),
                    content.getDepartment(),
                    content.getPriority(),
                    content.getDeadline() != null ? content.getDeadline().toString() : null // convert to String
            );
        }

        DocumentBasicDto docDto = new DocumentBasicDto(
                document.getId(),
                document.getFilename(),
                document.getOriginalFilename(),
                document.getCloudUrl(),
                contentDto
        );

        UserBasicDto managerDto = new UserBasicDto(
                manager.getId(),
                manager.getUsername(),
                manager.getEmail(),
                manager.getFullName()
        );

        UserBasicDto adminDto = new UserBasicDto(
                admin.getId(),
                admin.getUsername(),
                admin.getEmail(),
                admin.getFullName()
        );

        // 🔹 Return DTO (check WorkflowResponseDto constructor matches fields)
        return new WorkflowResponseDto(
                workflow.getId(),
                docDto,
                managerDto,
                adminDto,
                workflow.getCurrentStatus().name()
        );
    }


    // Manager -> assign to employee
    @Transactional
    public DocumentWorkflow assignToEmployee(Long workflowId,
                                             Long employeeId,
                                             LocalDateTime deadline,
                                             String comments,
                                             UserPrincipal currentUser) {
        DocumentWorkflow parentWorkflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Parent workflow not found"));

        User manager = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        if (manager.getDepartment() == null ||
                parentWorkflow.getAssignedTo() == null ||
                !manager.getDepartment().getId().equals(parentWorkflow.getAssignedTo().getDepartment().getId())) {
            throw new RuntimeException("Manager cannot assign tasks for this workflow");
        }

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (employee.getDepartment() == null || !employee.getDepartment().getId().equals(manager.getDepartment().getId())) {
            throw new RuntimeException("Employee must belong to the manager's department");
        }

        DocumentWorkflow workflow = new DocumentWorkflow();
        workflow.setDocument(parentWorkflow.getDocument());
        workflow.setCreatedBy(manager);
        workflow.setAssignedTo(employee);
        workflow.setCurrentStatus(DocumentWorkflow.WorkflowStatus.PENDING);
        workflow.setComments(comments);
        workflow.setDeadline(deadline);
        workflow.setCreatedAt(LocalDateTime.now());

        workflowRepository.save(workflow);

        auditService.logAction(manager.getId(), parentWorkflow.getDocument().getId(), "TASK_ASSIGNED",
                "Manager assigned to employee " + employee.getFullName());
        notificationService.sendTaskNotification(workflow);

        // CHANGE: unproxy before returning
        return (DocumentWorkflow) Hibernate.unproxy(workflow);
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

        auditService.logAction(currentUser.getId(), workflow.getDocument().getId(),
                "WORKFLOW_STATUS_UPDATED",
                "Status changed from " + oldStatus + " to " + newStatus + ". Comments: " + comments);

        return (DocumentWorkflow) Hibernate.unproxy(workflow);  // Also unproxy here
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
        List<DocumentWorkflow> upcomingDeadlines = getUpcomingDeadlines(1);
        for (DocumentWorkflow workflow : upcomingDeadlines) {
            notificationService.sendDeadlineReminder(workflow);
        }

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
