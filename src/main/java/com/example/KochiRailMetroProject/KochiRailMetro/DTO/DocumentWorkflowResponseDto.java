package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.Data;

@Data
public class DocumentWorkflowResponseDto {
    private Long workflowId;
    private Long documentId;
    private String filename;
    private String department;
    private String priority;
    private String summary;
    private String mlProcessingStatus;
    private String assignedTo;
    private String currentStatus;
}

