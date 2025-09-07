package com.example.KochiRailMetroProject.KochiRailMetro.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_workflows")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentWorkflow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "document_id")
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type")
    private WorkflowType workflowType;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status")
    private WorkflowStatus currentStatus = WorkflowStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "priority_level")
    private Integer priorityLevel = 1; // 1=Low, 2=Medium, 3=High, 4=Critical

    @Column(columnDefinition = "TEXT")
    private String comments;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum WorkflowType {
        REGULATORY_COMPLIANCE,
        MAINTENANCE_APPROVAL,
        SAFETY_REVIEW,
        PROCUREMENT_APPROVAL,
        HR_POLICY_REVIEW,
        INCIDENT_INVESTIGATION
    }

    public enum WorkflowStatus {
        PENDING,
        IN_PROGRESS,
        UNDER_REVIEW,
        APPROVED,
        REJECTED,
        COMPLETED,
        EXPIRED
    }
}

