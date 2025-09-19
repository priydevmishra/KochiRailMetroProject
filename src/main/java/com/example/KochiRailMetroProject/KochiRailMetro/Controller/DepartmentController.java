package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DepartmentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.DepartmentService;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.DocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DocumentService documentService;

    public DepartmentController(DepartmentService departmentService,
                                DocumentService documentService) {
        this.departmentService = departmentService;
        this.documentService = documentService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<List<DepartmentDto>>> getAllDepartments() {
        List<DepartmentDto> departments = departmentService.getAllDepartments();
        return ResponseEntity.ok(new ApiResponse<>(true, "Departments retrieved successfully", departments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentDto>> getDepartment(@PathVariable Long id) {
        DepartmentDto department = departmentService.getDepartmentById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Department retrieved successfully", department));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentDto>> createDepartment(@RequestBody DepartmentDto departmentDto) {
        try {
            DepartmentDto department = departmentService.createDepartment(departmentDto);
            return ResponseEntity.ok(new ApiResponse<>(true, "Department created successfully", department));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage()));
        }
    }

    @GetMapping("/operations/dashboard")
    public ResponseEntity<ApiResponse<OperationsDashboard>> getOperationsDashboard(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        // Get recent documents relevant to operations
        Pageable pageable = PageRequest.of(0, 10);
        // This would be implemented to filter by operations-specific criteria

        OperationsDashboard dashboard = new OperationsDashboard();
        dashboard.setMessage("Operations dashboard - train schedules, incidents, maintenance alerts");

        return ResponseEntity.ok(new ApiResponse<>(true, "Operations dashboard retrieved", dashboard));
    }

    @GetMapping("/engineering/dashboard")
    public ResponseEntity<ApiResponse<EngineeringDashboard>> getEngineeringDashboard(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        EngineeringDashboard dashboard = new EngineeringDashboard();
        dashboard.setMessage("Engineering dashboard - technical docs, maintenance reports, design changes");

        return ResponseEntity.ok(new ApiResponse<>(true, "Engineering dashboard retrieved", dashboard));
    }

    @GetMapping("/compliance/dashboard")
    public ResponseEntity<ApiResponse<ComplianceDashboard>> getComplianceDashboard(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        ComplianceDashboard dashboard = new ComplianceDashboard();
        dashboard.setMessage("Compliance dashboard - regulatory docs, deadlines, audit reports");

        return ResponseEntity.ok(new ApiResponse<>(true, "Compliance dashboard retrieved", dashboard));
    }

    // Inner classes for Avobe dashboard responses
    public static class OperationsDashboard {
        private String message;
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class EngineeringDashboard {
        private String message;
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class ComplianceDashboard {
        private String message;
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
