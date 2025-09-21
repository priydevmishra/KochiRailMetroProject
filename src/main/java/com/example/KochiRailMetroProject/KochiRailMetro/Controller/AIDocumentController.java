package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.*;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.AIDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/ai")
public class AIDocumentController {

    private static final Logger logger = LoggerFactory.getLogger(AIDocumentController.class);

    private final AIDocumentService aiDocumentService;
    private final DocumentRepository documentRepository;

    public AIDocumentController(AIDocumentService aiDocumentService,
                                DocumentRepository documentRepository) {
        this.aiDocumentService = aiDocumentService;
        this.documentRepository = documentRepository;
    }

    /**
     * Process single document with AI
     */
    @PostMapping("/process-document/{documentId}")
    public ResponseEntity<ApiResponse<AIProcessingResultDto>> processDocument(
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        logger.info("AI processing request for document {} by user {}", documentId, currentUser.getId());
        try {
            if (aiDocumentService.isDocumentProcessedByAI(documentId)) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Document has already been processed by AI"));
            }

            AIProcessingResultDto result = aiDocumentService.processDocumentWithAI(documentId, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "Document processed successfully with AI. Department: " + result.getDepartment()
                            + ", Priority: " + result.getPriority(),
                    result));

        } catch (Exception e) {
            logger.error("Error processing document {} with AI: {}", documentId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to process document with AI: " + e.getMessage()));
        }
    }

    /**
     * Process multiple documents with AI (async)
     */
    @PostMapping("/process-documents")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> processMultipleDocuments(
            @RequestBody BulkAIProcessingRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        List<Long> documentIds = requestDto.getDocumentIds();
        logger.info("Bulk AI processing request for {} documents by user {}", documentIds.size(), currentUser.getId());

        if (documentIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "No document IDs provided", null));
        }

        if (documentIds.size() > 10) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Maximum 10 documents can be processed at once", null));
        }

        // Process each document async and collect results
        List<CompletableFuture<Map<String, Object>>> futures = documentIds.stream()
                .map(documentId -> CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> resultMap = new HashMap<>();
                    resultMap.put("documentId", documentId);

                    try {
                        AIProcessingResultDto result = aiDocumentService.processDocumentWithAI(documentId, currentUser);
                        resultMap.put("success", true);
                        resultMap.put("department", result.getDepartment());
                        resultMap.put("priority", result.getPriority());
                    } catch (Exception e) {
                        resultMap.put("success", false);
                        resultMap.put("error", e.getMessage());
                    }

                    return resultMap;
                }))
                .toList();

        // Wait for all async tasks to complete
        List<Map<String, Object>> responseList = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Bulk AI processing completed", responseList)
        );
    }

    /**
     * Check if document has been processed by AI
     */
    @GetMapping("/check-status/{documentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkProcessingStatus(
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            boolean isProcessed = aiDocumentService.isDocumentProcessedByAI(documentId);
            Map<String, Object> status = Map.of(
                    "documentId", documentId,
                    "isProcessed", isProcessed,
                    "status", isProcessed ? "PROCESSED" : "NOT_PROCESSED"
            );
            return ResponseEntity.ok(new ApiResponse<>(true, "Processing status retrieved successfully", status));
        } catch (Exception e) {
            logger.error("Error checking AI processing status for document {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to check processing status: " + e.getMessage()));
        }
    }

    /**
     * Get AI processing statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAIStats(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            Map<String, Object> stats = Map.of(
                    "message", "AI statistics endpoint - implement based on requirements",
                    "totalProcessed", 0,
                    "pendingProcessing", 0,
                    "failedProcessing", 0
            );

            return ResponseEntity.ok(new ApiResponse<>(true, "AI processing statistics retrieved", stats));
        } catch (Exception e) {
            logger.error("Error retrieving AI stats: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to retrieve AI statistics: " + e.getMessage()));
        }
    }
}
