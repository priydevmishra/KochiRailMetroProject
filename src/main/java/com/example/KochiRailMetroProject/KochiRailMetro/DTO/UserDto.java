package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String employeeId;
    private String phoneNumber;
    private Boolean isActive;
    private String notificationPreferences;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Department information
    private Long departmentId;
    private String departmentName;
    private String departmentCode;

    // Role information
    private Set<String> roles;

    // Manager information (for employees)
    private String managerName;
    private String managerEmail;
}
