package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.UserRegistrationDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.UserDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DepartmentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.UserRegistrationService;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.DepartmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Validated
@CrossOrigin(origins = "http://localhost:5173")
public class UserRegistrationController {

    private final UserRegistrationService userRegistrationService;
    private final DepartmentService departmentService;

    public UserRegistrationController(UserRegistrationService userRegistrationService,
                                      DepartmentService departmentService) {
        this.userRegistrationService = userRegistrationService;
        this.departmentService = departmentService;
    }

    // 🔹 Admin can register Managers (Admin chooses Department from dropdown)
    @PostMapping("/register/manager")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> registerManager(
            @Valid @RequestBody UserRegistrationDto registrationDto,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        UserDto createdUser = userRegistrationService.registerManager(registrationDto, currentUser);
        return ResponseEntity.ok(new ApiResponse<>(true, "Manager registered successfully", createdUser));
    }

    // 🔹 Manager can register Employees (EmployeeId auto-generated, Dept auto-mapped)
    @PostMapping("/register/employee")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<UserDto>> registerEmployee(
            @Valid @RequestBody UserRegistrationDto registrationDto,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        // auto-assign departmentId from manager
        if (registrationDto.getDepartmentId() == null) {
            registrationDto.setDepartmentId(currentUser.getDepartmentId());
        }

        // employeeId auto-generate in service (ignore whatever comes from request)
        UserDto createdUser = userRegistrationService.registerEmployee(registrationDto, currentUser);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Employee registered successfully", createdUser)
        );
    }

    // Get all managers (Admin only)
    @GetMapping("/managers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllManagers() {
        List<UserDto> managers = userRegistrationService.getAllManagers();
        return ResponseEntity.ok(new ApiResponse<>(true, "Managers retrieved successfully", managers));
    }

    // Get employees in manager's department
    @GetMapping("/employees")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<List<UserDto>>> getDepartmentEmployees(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        List<UserDto> employees = userRegistrationService.getEmployeesByManager(currentUser);
        return ResponseEntity.ok(new ApiResponse<>(true, "Department employees retrieved successfully", employees));
    }

    // Get all employees (Admin only)
    @GetMapping("/employees/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllEmployees() {
        List<UserDto> employees = userRegistrationService.getAllEmployees();
        return ResponseEntity.ok(new ApiResponse<>(true, "All employees retrieved successfully", employees));
    }

    // Update user (Admin can update managers, Manager can update employees, Users can update themselves)
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserRegistrationDto updateDto,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserDto updatedUser = userRegistrationService.updateUser(userId, updateDto, currentUser);
        return ResponseEntity.ok(new ApiResponse<>(true, "User updated successfully", updatedUser));
    }

    // Delete manager (Admin only)
    @DeleteMapping("/manager/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteManager(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userRegistrationService.deleteManager(userId, currentUser);
        return ResponseEntity.ok(new ApiResponse<>(true, "Manager deleted successfully"));
    }

    // Delete employee (Manager can delete employees in their department)
    @DeleteMapping("/employee/{userId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<String>> deleteEmployee(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userRegistrationService.deleteEmployee(userId, currentUser);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee deleted successfully"));
    }

    // Get available departments for registration (Admin creates manager, so dropdown needed here)
    @GetMapping("/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DepartmentDto>>> getAvailableDepartments() {
        List<DepartmentDto> departments = departmentService.getAllDepartments();
        return ResponseEntity.ok(new ApiResponse<>(true, "Departments retrieved successfully", departments));
    }

    // Get user profile
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> getUserProfile(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserDto userProfile = userRegistrationService.getUserProfile(currentUser);
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile retrieved successfully", userProfile));
    }

    // Update own profile (All users can update their own details)
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> updateOwnProfile(
            @Valid @RequestBody UserRegistrationDto updateDto,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserDto updatedUser = userRegistrationService.updateOwnProfile(updateDto, currentUser);
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile updated successfully", updatedUser));
    }
}
