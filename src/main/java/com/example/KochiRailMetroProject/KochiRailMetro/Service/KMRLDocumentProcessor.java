package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.*;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DepartmentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentWorkflowRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class KMRLDocumentProcessor {

    private final DepartmentRepository departmentRepository;
    private final DocumentWorkflowRepository workflowRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public KMRLDocumentProcessor(DepartmentRepository departmentRepository,
                                 DocumentWorkflowRepository workflowRepository,
                                 NotificationService notificationService,
                                 AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.workflowRepository = workflowRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Async
    public void processKMRLDocument(Document document) {
        try {
            // Auto-categorize and tag based on content
            autoCategorizeBySender(document);
            autoTagByContent(document);

            // Process based on document type
            if (isRegulatoryDocument(document)) {
                processRegulatoryDocument(document);
            } else if (isMaintenanceDocument(document)) {
                processMaintenanceDocument(document);
            } else if (isSafetyDocument(document)) {
                processSafetyDocument(document);
            } else if (isIncidentDocument(document)) {
                processIncidentDocument(document);
            } else if (isHRDocument(document)) {
                processHRDocument(document);
            }

        } catch (Exception e) {
            auditService.logAction(null, document.getId(), "PROCESSING_ERROR",
                    "Error in KMRL document processing: " + e.getMessage());
        }
    }

    private void autoCategorizeBySender(Document document) {
        if (document.getDocumentSource() == Document.DocumentSource.GMAIL) {
            // Check Gmail metadata for sender
            String sender = getMetadataValue(document, "sender");
            if (sender != null) {
                sender = sender.toLowerCase();

                if (sender.contains("cmrs") || sender.contains("metro") || sender.contains("regulatory")) {
                    addTag(document, "regulatory");
                } else if (sender.contains("maintenance") || sender.contains("engineering")) {
                    addTag(document, "maintenance");
                } else if (sender.contains("safety") || sender.contains("security")) {
                    addTag(document, "safety");
                } else if (sender.contains("hr") || sender.contains("human")) {
                    addTag(document, "hr");
                }
            }
        }
    }

    private void autoTagByContent(Document document) {
        if (document.getContent() != null && document.getContent().getExtractedText() != null) {
            String content = document.getContent().getExtractedText().toLowerCase();

            // Railway/Metro specific keywords
            if (content.contains("train") || content.contains("rolling stock")) {
                addTag(document, "rolling-stock");
            }
            if (content.contains("station") || content.contains("platform")) {
                addTag(document, "station-operations");
            }
            if (content.contains("maintenance") || content.contains("repair")) {
                addTag(document, "maintenance");
            }
            if (content.contains("safety") || content.contains("incident")) {
                addTag(document, "safety");
            }
            if (content.contains("procurement") || content.contains("purchase")) {
                addTag(document, "procurement");
            }

            // Extract train numbers
            Pattern trainPattern = Pattern.compile("train\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = trainPattern.matcher(content);
            while (matcher.find()) {
                addTag(document, "train-" + matcher.group(1));
            }
        }
    }

    public void processRegulatoryDocument(Document document) {
        // Create regulatory compliance workflow
        DocumentWorkflow workflow = new DocumentWorkflow();
        workflow.setDocument(document);
        workflow.setWorkflowType(DocumentWorkflow.WorkflowType.REGULATORY_COMPLIANCE);
        workflow.setCurrentStatus(DocumentWorkflow.WorkflowStatus.PENDING);
        workflow.setPriorityLevel(3); // High priority for regulatory

        // Set deadline (typically 30 days for compliance)
        workflow.setDeadline(LocalDateTime.now().plusDays(30));

        // Assign to compliance department head
        Department complianceDept = departmentRepository.findByCode("COMPLIANCE").orElse(null);
        if (complianceDept != null) {
            // Find department head and assign
            workflow.setAssignedTo(findDepartmentHead(complianceDept));
        }

        workflowRepository.save(workflow);

        // Send notifications to all department heads
        notificationService.sendRegulatoryAlert(document);

        addTag(document, "regulatory");
        addTag(document, "compliance-required");

        auditService.logAction(null, document.getId(), "REGULATORY_PROCESSING",
                "Regulatory document processed with compliance workflow");
    }

    public void processMaintenanceDocument(Document document) {
        addTag(document, "maintenance");

        // Extract maintenance details from content
        if (document.getContent() != null && document.getContent().getExtractedText() != null) {
            String content = document.getContent().getExtractedText().toLowerCase();

            // Check for urgency indicators
            if (content.contains("urgent") || content.contains("immediate") ||
                    content.contains("critical") || content.contains("emergency")) {

                // Create urgent maintenance workflow
                DocumentWorkflow workflow = new DocumentWorkflow();
                workflow.setDocument(document);
                workflow.setWorkflowType(DocumentWorkflow.WorkflowType.MAINTENANCE_APPROVAL);
                workflow.setPriorityLevel(4); // Critical
                workflow.setDeadline(LocalDateTime.now().plusHours(4)); // 4 hours for urgent

                Department engineeringDept = departmentRepository.findByCode("ENGINEERING").orElse(null);
                if (engineeringDept != null) {
                    workflow.setAssignedTo(findDepartmentHead(engineeringDept));
                }

                workflowRepository.save(workflow);

                addTag(document, "urgent");
                notificationService.sendMaintenanceAlert(document, true);
            } else {
                // Regular maintenance - lower priority
                notificationService.sendMaintenanceAlert(document, false);
            }
        }
    }

    public void processSafetyDocument(Document document) {
        addTag(document, "safety");

        // Safety documents always high priority
        DocumentWorkflow workflow = new DocumentWorkflow();
        workflow.setDocument(document);
        workflow.setWorkflowType(DocumentWorkflow.WorkflowType.SAFETY_REVIEW);
        workflow.setPriorityLevel(3); // High priority
        workflow.setDeadline(LocalDateTime.now().plusDays(7)); // 1 week for safety review

        workflowRepository.save(workflow);

        // Notify all departments for safety bulletins
        notificationService.sendSafetyBulletin(document);

        auditService.logAction(null, document.getId(), "SAFETY_PROCESSING",
                "Safety document processed with mandatory review workflow");
    }

    public void processIncidentDocument(Document document) {
        addTag(document, "incident");
        addTag(document, "investigation-required");

        // Create incident investigation workflow
        DocumentWorkflow workflow = new DocumentWorkflow();
        workflow.setDocument(document);
        workflow.setWorkflowType(DocumentWorkflow.WorkflowType.INCIDENT_INVESTIGATION);
        workflow.setPriorityLevel(4); // Critical - incidents need immediate attention
        workflow.setDeadline(LocalDateTime.now().plusDays(3)); // 72 hours for incident response

        workflowRepository.save(workflow);

        // Immediate notification to safety and operations
        notificationService.sendIncidentAlert(document);

        auditService.logAction(null, document.getId(), "INCIDENT_PROCESSING",
                "Incident document processed with investigation workflow");
    }

    public void processHRDocument(Document document) {
        addTag(document, "hr");

        if (document.getContent() != null && document.getContent().getExtractedText() != null) {
            String content = document.getContent().getExtractedText().toLowerCase();

            if (content.contains("policy") || content.contains("circular")) {
                // HR policy documents need review
                DocumentWorkflow workflow = new DocumentWorkflow();
                workflow.setDocument(document);
                workflow.setWorkflowType(DocumentWorkflow.WorkflowType.HR_POLICY_REVIEW);
                workflow.setPriorityLevel(2); // Medium priority
                workflow.setDeadline(LocalDateTime.now().plusDays(14)); // 2 weeks for policy review

                workflowRepository.save(workflow);
                addTag(document, "policy");
            }
        }

        // Notify HR department
        notificationService.sendHRNotification(document);
    }

    // Helper methods
    private boolean isRegulatoryDocument(Document document) {
        String content = getDocumentContent(document);
        String sender = getMetadataValue(document, "sender");

        if (sender != null && (sender.toLowerCase().contains("cmrs") ||
                sender.toLowerCase().contains("ministry") ||
                sender.toLowerCase().contains("regulatory"))) {
            return true;
        }

        return content != null && (
                content.toLowerCase().contains("regulatory") ||
                        content.toLowerCase().contains("compliance") ||
                        content.toLowerCase().contains("audit") ||
                        content.toLowerCase().contains("inspection")
        );
    }

    private boolean isMaintenanceDocument(Document document) {
        String content = getDocumentContent(document);
        return content != null && (
                content.toLowerCase().contains("maintenance") ||
                        content.toLowerCase().contains("repair") ||
                        content.toLowerCase().contains("overhaul") ||
                        content.toLowerCase().contains("inspection")
        );
    }

    private boolean isSafetyDocument(Document document) {
        String content = getDocumentContent(document);
        String subject = getMetadataValue(document, "subject");

        return (content != null && (
                content.toLowerCase().contains("safety") ||
                        content.toLowerCase().contains("accident") ||
                        content.toLowerCase().contains("hazard")
        )) || (subject != null && subject.toLowerCase().contains("safety"));
    }

    private boolean isIncidentDocument(Document document) {
        String content = getDocumentContent(document);
        return content != null && (
                content.toLowerCase().contains("incident") ||
                        content.toLowerCase().contains("accident") ||
                        content.toLowerCase().contains("failure") ||
                        content.toLowerCase().contains("breakdown")
        );
    }

    private boolean isHRDocument(Document document) {
        String sender = getMetadataValue(document, "sender");
        String content = getDocumentContent(document);

        return (sender != null && sender.toLowerCase().contains("hr")) ||
                (content != null && (
                        content.toLowerCase().contains("employee") ||
                                content.toLowerCase().contains("policy") ||
                                content.toLowerCase().contains("training")
                ));
    }

    private String getDocumentContent(Document document) {
        if (document.getContent() != null) {
            return document.getContent().getExtractedText();
        }
        return null;
    }

    private String getMetadataValue(Document document, String key) {
        if (document.getMetadata() != null) {
            return document.getMetadata().stream()
                    .filter(meta -> key.equals(meta.getKey()))
                    .map(DocumentMetadata::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private void addTag(Document document, String tagName) {
        if (document.getTags() == null) {
            document.setTags(new HashSet<>());
        }

        Tag tag = new Tag();
        tag.setName(tagName);
        document.getTags().add(tag);
    }

    private User findDepartmentHead(Department department) {
        // This would typically query users with HEAD role in the department
        // For now, return null - implement based on your user hierarchy
        return null;
    }
}
