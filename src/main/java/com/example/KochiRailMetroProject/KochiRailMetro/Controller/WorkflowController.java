package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentWorkflowResponseDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.WorkflowResponseDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentWorkflow;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.WorkflowService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/workflows") // Ye api poori work flow ke liye hai... isko acche se test karna...
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    // Admin -> Manager auto assign
    @PostMapping("/assign-to-manager")
    public ResponseEntity<ApiResponse<WorkflowResponseDto>> assignToManager(
            @RequestParam Long documentId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        // Call service method
        WorkflowResponseDto dto = workflowService.assignToDepartmentManager(documentId, currentUser);

        // Return in ApiResponse
        return ResponseEntity.ok(new ApiResponse<>(true, "Assigned to manager", dto));
    }

    // Manager -> Employee assign
    @PostMapping("/assign-to-employee")
    public ResponseEntity<ApiResponse<DocumentWorkflow>> assignToEmployee(
            @RequestParam Long workflowId,
            @RequestParam Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline,
            @RequestParam(required = false) String comments,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        DocumentWorkflow wf = workflowService.assignToEmployee(workflowId, employeeId, deadline, comments, currentUser);
        return ResponseEntity.ok(new ApiResponse<>(true, "Assigned to employee", wf));
    }

    // My workflows (paged)
    @GetMapping("/my-workflows")
    public ResponseEntity<ApiResponse<Page<DocumentWorkflow>>> getMyWorkflows(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DocumentWorkflow> workflows = workflowService.getMyWorkflows(currentUser, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Workflows retrieved", workflows));
    }

    // iss method ko baad me acche se likhenge isko GPT pe daalke iski working likh lena..
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Page<DocumentWorkflow>>> getPendingWorkflows(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DocumentWorkflow> workflows = workflowService.getPendingWorkflows(currentUser, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Pending workflows retrieved", workflows));
    }

    // isko bhi baad me shi se design karenge...iskaa flow dekh kar likh lena...
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DocumentWorkflow>> updateWorkflowStatus(
            @PathVariable Long id,
            @RequestParam DocumentWorkflow.WorkflowStatus status,
            @RequestParam(required = false) String comments,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            DocumentWorkflow workflow = workflowService.updateWorkflowStatus(id, status, comments, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true, "Workflow status updated", workflow));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, e.getMessage()));
        }
    }
}
