package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentWorkflow;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.WorkflowService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/my-workflows")
    public ResponseEntity<ApiResponse<Page<DocumentWorkflow>>> getMyWorkflows(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DocumentWorkflow> workflows = workflowService.getMyWorkflows(currentUser, pageable);

        return ResponseEntity.ok(new ApiResponse<>(true, "Workflows retrieved successfully", workflows));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Page<DocumentWorkflow>>> getPendingWorkflows(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DocumentWorkflow> workflows = workflowService.getPendingWorkflows(currentUser, pageable);

        return ResponseEntity.ok(new ApiResponse<>(true, "Pending workflows retrieved successfully", workflows));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DocumentWorkflow>> updateWorkflowStatus(
            @PathVariable Long id,
            @RequestParam DocumentWorkflow.WorkflowStatus status,
            @RequestParam(required = false) String comments,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            DocumentWorkflow workflow = workflowService.updateWorkflowStatus(id, status, comments, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true, "Workflow status updated successfully", workflow));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage()));
        }
    }
}
