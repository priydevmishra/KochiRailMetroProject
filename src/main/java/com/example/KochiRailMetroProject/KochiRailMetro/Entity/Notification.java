package com.example.KochiRailMetroProject.KochiRailMetro.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "action_required")
    private Boolean actionRequired = false;

    @Column(name = "action_url")
    private String actionUrl;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public enum NotificationType {
        DOCUMENT_UPLOADED,
        REGULATORY_ALERT,
        DEADLINE_REMINDER,
        WORKFLOW_ASSIGNED,
        MAINTENANCE_ALERT,
        SAFETY_BULLETIN,
        COMPLIANCE_WARNING
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
