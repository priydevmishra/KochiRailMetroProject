package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.AIDocumentRequestDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.AIDocumentResponseDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.AIProcessingResultDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentStatusDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.*;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentWorkflowRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DepartmentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
public class AIDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(AIDocumentService.class);

    @Value("${ai.ml.service.url:https://metrixsync-ai-1.onrender.com}")
    private String aiServiceUrl;

    @Value("${ai.ml.service.endpoint:/process-document}")
    private String aiServiceEndpoint;

    private final DocumentRepository documentRepository;
    private final DocumentWorkflowRepository workflowRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AIDocumentService(DocumentRepository documentRepository,
                             DocumentWorkflowRepository workflowRepository,
                             DepartmentRepository departmentRepository,
                             UserRepository userRepository,
                             AuditService auditService,
                             NotificationService notificationService,
                             RestTemplate restTemplate,
                             ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.workflowRepository = workflowRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<DocumentStatusDto> getAllDocumentsStatus() {
        List<Document> documents = documentRepository.findAll();
        List<DocumentStatusDto> result = new ArrayList<>();

        for (Document doc : documents) {
            DocumentStatusDto dto = new DocumentStatusDto();
            dto.setDocumentId(doc.getId());

            if (doc.getContent() != null &&
                    doc.getContent().getMlSummary() != null &&
                    !doc.getContent().getMlSummary().startsWith("Error:")) {

                dto.setDepartment(doc.getContent().getDepartment());
                dto.setPriority(doc.getContent().getPriority());
                dto.setSummary(doc.getContent().getMlSummary());
                dto.setDeadline(doc.getContent().getDeadline() != null
                        ? doc.getContent().getDeadline().toString()
                        : null);
                dto.setMlProcessingStatus(doc.getContent().getProcessingStatus().name());
                dto.setStatus("PROCESSED");

            } else {
                dto.setStatus("NOT_PROCESSED");
                dto.setMlProcessingStatus("PENDING");
            }
            result.add(dto);
        }
        return result;
    }


    /**
     * Send document to AI service for processing
     */
    public AIProcessingResultDto processDocumentWithAI(Long documentId, UserPrincipal currentUser) {
        logger.info("Starting AI processing for document ID: {} by user: {}", documentId, currentUser.getId());

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found with ID: " + documentId));

        if (document.getIsDeleted()) {
            throw new RuntimeException("Cannot process deleted document");
        }

        try {
            // Prepare request for AI service
            AIDocumentRequestDto request = createAIRequest(document);

            // Send to AI service
            AIDocumentResponseDto aiResponse = sendToAIService(request);

            if (aiResponse.getError() != null) {
                logger.error("AI service returned error for document {}: {}", documentId, aiResponse.getError());
                throw new RuntimeException("AI processing failed: " + aiResponse.getError());
            }

            // Process AI response
            AIProcessingResultDto result = processAIResponse(document, aiResponse, currentUser);

            // Log audit
            auditService.logAction(currentUser.getId(), document.getId(), "AI_PROCESSING",
                    "Document sent to AI service. Department: " + result.getDepartment() +
                            ", Priority: " + result.getPriority());

            logger.info("AI processing completed successfully for document ID: {}", documentId);
            return result;

        } catch (Exception e) {
            logger.error("Error in AI processing for document {}: {}", documentId, e.getMessage(), e);

            // Update document content with error status
            updateDocumentWithError(document, "AI processing failed: " + e.getMessage());

            auditService.logAction(currentUser.getId(), document.getId(), "AI_PROCESSING_ERROR",
                    "AI processing failed: " + e.getMessage());

            throw new RuntimeException("Failed to process document with AI: " + e.getMessage(), e);
        }
    }

    /**
     * Async version for bulk processing
     */
    @Async
    public CompletableFuture<AIProcessingResultDto> processDocumentWithAIAsync(Long documentId, UserPrincipal currentUser) {
        try {
            AIProcessingResultDto result = processDocumentWithAI(documentId, currentUser);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<AIProcessingResultDto> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private AIDocumentRequestDto createAIRequest(Document document) {
        AIDocumentRequestDto request = new AIDocumentRequestDto();
        request.setDocumentId(String.valueOf(document.getId()));
        request.setName(document.getOriginalFilename());
        request.setSource(document.getDocumentSource().name());
        request.setCloudinaryUrl(document.getCloudinarySecureUrl());

        logger.debug("Created AI request for document {}: {}", document.getId(), request);
        return request;
    }

    private AIDocumentResponseDto sendToAIService(AIDocumentRequestDto request) {
        String fullUrl = aiServiceUrl + aiServiceEndpoint;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AIDocumentRequestDto> requestEntity = new HttpEntity<>(request, headers);

        try {
            logger.info("Sending request to AI service: {}", fullUrl);
            logger.debug("Request payload: {}", request);

            ResponseEntity<AIDocumentResponseDto> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    requestEntity,
                    AIDocumentResponseDto.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Received successful response from AI service: {}", response.getBody());
                return response.getBody();
            } else {
                throw new RuntimeException("AI service returned unsuccessful response: " + response.getStatusCode());
            }

        } catch (ResourceAccessException e) {
            logger.error("Failed to connect to AI service at {}: {}", fullUrl, e.getMessage());
            throw new RuntimeException("AI service is not accessible. Please check if the ML service is running.", e);
        } catch (Exception e) {
            logger.error("Error calling AI service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to communicate with AI service", e);
        }
    }

    private AIProcessingResultDto processAIResponse(Document document, AIDocumentResponseDto aiResponse, UserPrincipal currentUser) {
        logger.info("Processing AI response for document {}: Department={}, Priority={}, Deadline={}",
                document.getId(), aiResponse.getDepartment(), aiResponse.getPriority(), aiResponse.getDeadline());

        // Update document content with AI summary
        updateDocumentContent(document, aiResponse);

        // Create workflow if needed
        createWorkflowFromAIResponse(document, aiResponse, currentUser);

        // Parse deadline
        LocalDate deadline = parseDeadline(aiResponse.getDeadline());

        // Create result DTO
        AIProcessingResultDto result = new AIProcessingResultDto();
        result.setDocumentId(document.getId());
        result.setDepartment(aiResponse.getDepartment());
        result.setPriority(aiResponse.getPriority());
        result.setDeadline(deadline);
        result.setSummary(aiResponse.getSummary());
        result.setMlProcessingStatus("COMPLETED");

        return result;
    }

    private void updateDocumentContent(Document document, AIDocumentResponseDto aiResponse) {
        DocumentContent content = document.getContent();
        if (content == null) {
            content = new DocumentContent();
            content.setDocument(document);
            document.setContent(content);
        }

        // Update ML summary and processing status
        content.setMlSummary(aiResponse.getSummary());
        content.setProcessingStatus(DocumentContent.ProcessingStatus.COMPLETED);
        content.setProcessedAt(LocalDateTime.now());

        // Save the document with updated content
        documentRepository.save(document);
        logger.info("Updated document {} with AI summary", document.getId());
    }

    private void createWorkflowFromAIResponse(Document document, AIDocumentResponseDto aiResponse, UserPrincipal currentUser) {
        try {
            DocumentWorkflow workflow = new DocumentWorkflow();
            workflow.setDocument(document);

            // Map AI department to workflow type
            DocumentWorkflow.WorkflowType workflowType = mapDepartmentToWorkflowType(aiResponse.getDepartment());
            workflow.setWorkflowType(workflowType);

            // Map AI priority to priority level
            int priorityLevel = mapPriorityToLevel(aiResponse.getPriority());
            workflow.setPriorityLevel(priorityLevel);

            workflow.setCurrentStatus(DocumentWorkflow.WorkflowStatus.PENDING);

            // Parse and set deadline
            LocalDate deadlineDate = parseDeadline(aiResponse.getDeadline());
            if (deadlineDate != null) {
                workflow.setDeadline(deadlineDate.atTime(17, 0)); // Set to 5 PM on deadline date
            }

            // Set creator
            User currentUserEntity = userRepository.findById(currentUser.getId()).orElse(null);
            workflow.setCreatedBy(currentUserEntity);

            // Try to assign to department head
            assignToDepartmentHead(workflow, aiResponse.getDepartment());

            // Add comments from AI
            workflow.setComments("AI Analysis - Department: " + aiResponse.getDepartment() +
                    ", Priority: " + aiResponse.getPriority() +
                    ". Summary: " + (aiResponse.getSummary().length() > 100 ?
                    aiResponse.getSummary().substring(0, 100) + "..." : aiResponse.getSummary()));

            workflowRepository.save(workflow);
            logger.info("Created workflow for document {} with type {} and priority {}",
                    document.getId(), workflowType, priorityLevel);

        } catch (Exception e) {
            logger.error("Failed to create workflow from AI response for document {}: {}",
                    document.getId(), e.getMessage(), e);
            // Don't throw exception here as the main processing was successful
        }
    }

    private DocumentWorkflow.WorkflowType mapDepartmentToWorkflowType(String department) {
        if (department == null) return DocumentWorkflow.WorkflowType.REGULATORY_COMPLIANCE;

        switch (department.toUpperCase()) {
            case "HR":
                return DocumentWorkflow.WorkflowType.HR_POLICY_REVIEW;
            case "MAINTENANCE":
            case "ENGINEERING":
                return DocumentWorkflow.WorkflowType.MAINTENANCE_APPROVAL;
            case "SAFETY":
                return DocumentWorkflow.WorkflowType.SAFETY_REVIEW;
            case "PROCUREMENT":
                return DocumentWorkflow.WorkflowType.PROCUREMENT_APPROVAL;
            case "INCIDENT":
                return DocumentWorkflow.WorkflowType.INCIDENT_INVESTIGATION;
            default:
                return DocumentWorkflow.WorkflowType.REGULATORY_COMPLIANCE;
        }
    }

    private int mapPriorityToLevel(String priority) {
        if (priority == null) return 2; // Medium as default

        switch (priority.toUpperCase()) {
            case "LOW":
                return 1;
            case "MEDIUM":
                return 2;
            case "HIGH":
                return 3;
            case "CRITICAL":
                return 4;
            default:
                return 2; // Medium as default
        }
    }

    private LocalDate parseDeadline(String deadlineStr) {
        if (deadlineStr == null || deadlineStr.trim().isEmpty()) {
            return null;
        }

        try {
            // Try different date formats
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            };

            for (DateTimeFormatter formatter : formatters) {
                try {
                    return LocalDate.parse(deadlineStr.trim(), formatter);
                } catch (DateTimeParseException ignored) {
                    // Try next format
                }
            }

            // If no format worked, try ISO format
            return LocalDate.parse(deadlineStr.trim());

        } catch (Exception e) {
            logger.warn("Failed to parse deadline '{}': {}", deadlineStr, e.getMessage());
            return null;
        }
    }

    private void assignToDepartmentHead(DocumentWorkflow workflow, String departmentName) {
        if (departmentName == null) return;

        try {
            Optional<Department> department = departmentRepository.findByNameIgnoreCase(departmentName);
            if (department.isPresent()) {
                // Find department head - this would depend on your user hierarchy implementation
                // For now, we'll leave it unassigned
                logger.info("Found department {} for workflow assignment", departmentName);
            } else {
                logger.warn("Department '{}' not found for workflow assignment", departmentName);
            }
        } catch (Exception e) {
            logger.error("Error assigning workflow to department {}: {}", departmentName, e.getMessage());
        }
    }

    private void updateDocumentWithError(Document document, String errorMessage) {
        try {
            DocumentContent content = document.getContent();
            if (content == null) {
                content = new DocumentContent();
                content.setDocument(document);
                document.setContent(content);
            }

            content.setMlSummary("Error: " + errorMessage);
            content.setProcessingStatus(DocumentContent.ProcessingStatus.FAILED);
            content.setProcessedAt(LocalDateTime.now());

            documentRepository.save(document);
        } catch (Exception e) {
            logger.error("Failed to update document with error status: {}", e.getMessage());
        }
    }

    /**
     * Check if document has already been processed by AI
     */
    public boolean isDocumentProcessedByAI(Long documentId) {
        Optional<Document> document = documentRepository.findById(documentId);
        if (document.isPresent() && document.get().getContent() != null) {
            DocumentContent content = document.get().getContent();
            return content.getMlSummary() != null &&
                    !content.getMlSummary().trim().isEmpty() &&
                    !content.getMlSummary().startsWith("Error:");
        }
        return false;
    }
}
