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
import org.hibernate.Hibernate;

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

    // Helper method to convert Entity -> DTO
    private WorkflowResponseDto mapToDto(DocumentWorkflow wf) {
        return new WorkflowResponseDto(
                wf.getId(),
                new DocumentBasicDto(
                        wf.getDocument().getId(),
                        wf.getDocument().getFilename(),
                        wf.getDocument().getOriginalFilename(),
                        wf.getDocument().getCloudUrl(),
                        wf.getDocument().getContent() != null ? new DocumentContentDto(
                                wf.getDocument().getContent().getExtractedText(),
                                wf.getDocument().getContent().getSummary(),
                                wf.getDocument().getContent().getMlSummary(),
                                wf.getDocument().getContent().getDepartment(),
                                wf.getDocument().getContent().getPriority(),
                                wf.getDocument().getContent().getDeadline() != null ?
                                        wf.getDocument().getContent().getDeadline().toString() : null
                        ) : null
                ),
                wf.getAssignedTo() != null ? new UserBasicDto(
                        wf.getAssignedTo().getId(),
                        wf.getAssignedTo().getUsername(),
                        wf.getAssignedTo().getEmail(),
                        wf.getAssignedTo().getFullName()
                ) : null,
                wf.getAssignedBy() != null ? new UserBasicDto(
                        wf.getAssignedBy().getId(),
                        wf.getAssignedBy().getUsername(),
                        wf.getAssignedBy().getEmail(),
                        wf.getAssignedBy().getFullName()
                ) : null,
                wf.getCreatedBy() != null ? new UserBasicDto(
                        wf.getCreatedBy().getId(),
                        wf.getCreatedBy().getUsername(),
                        wf.getCreatedBy().getEmail(),
                        wf.getCreatedBy().getFullName()
                ) : null,
                wf.getCurrentStatus().name()
        );
    }


    // Admin -> auto assign to manager of document.department
    @Transactional
    public WorkflowResponseDto assignToDepartmentManager(Long documentId,
                                                         UserPrincipal currentUser) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Department department = document.getDepartment();
        if (department == null) {
            throw new RuntimeException("Document has no department assigned");
        }

        User manager = userRepository.findFirstManagerByDepartmentCode(department.getCode())
                .orElseThrow(() -> new RuntimeException("No manager found for department " + department.getCode()));

        User admin = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        DocumentWorkflow workflow = new DocumentWorkflow();
        workflow.setDocument(document);
        workflow.setCreatedBy(admin);
        workflow.setAssignedTo(manager);
        workflow.setCurrentStatus(DocumentWorkflow.WorkflowStatus.PENDING);

        DocumentContent content = document.getContent();
        if (content != null && content.getDeadline() != null) {
            workflow.setDeadline(content.getDeadline());
        }

        workflow.setCreatedAt(LocalDateTime.now());
        workflowRepository.save(workflow);

        auditService.logAction(admin.getId(), documentId, "TASK_ASSIGNED",
                "Admin auto-assigned to manager " + manager.getFullName() + " for dept " + department.getCode());

        notificationService.sendTaskNotification(workflow);

        // ✅ Instead of manually building dto here, reuse mapToDto
        return mapToDto(workflow);
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

        return (DocumentWorkflow) Hibernate.unproxy(workflow);
    }

    // Changed return type from Page<DocumentWorkflow> → Page<WorkflowResponseDto>
    public Page<WorkflowResponseDto> getMyWorkflows(UserPrincipal currentUser, Pageable pageable) {
        User user = new User();
        user.setId(currentUser.getId());
        return workflowRepository.findByAssignedTo(user, pageable)
                .map(this::mapToDto);  // convert each entity to dto
    }

    // Changed return type from Page<DocumentWorkflow> → Page<WorkflowResponseDto>
    public Page<WorkflowResponseDto> getPendingWorkflows(UserPrincipal currentUser, Pageable pageable) {
        User user = new User();
        user.setId(currentUser.getId()); // bas ID set karna enough hai

        List<DocumentWorkflow.WorkflowStatus> pendingStatuses = List.of(
                DocumentWorkflow.WorkflowStatus.PENDING,
                DocumentWorkflow.WorkflowStatus.IN_PROGRESS
        );

        // Repository call → Entity to DTO map
        return workflowRepository.findByAssignedToAndStatusIn(user, pendingStatuses, pageable)
                .map(this::mapToDto);
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

        return (DocumentWorkflow) Hibernate.unproxy(workflow);
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
