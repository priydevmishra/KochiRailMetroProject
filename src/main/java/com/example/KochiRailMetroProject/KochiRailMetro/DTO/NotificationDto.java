package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationDto {
    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    @NotBlank(message = "Type is required")
    private String type; // INFO, WARNING, URGENT, MAINTENANCE, etc.

    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    private String departmentName;

    private Boolean isRead;

    private LocalDateTime createdAt;

    // Sender information
    private String senderName;
    private String senderEmail;

    // Recipient information
    private String recipientName;
    private String recipientEmail;
}
