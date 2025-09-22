package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.Data;

@Data
public class DocumentStatusDto {
    private Long documentId;
    private String department;
    private String priority;
    private String summary;
    private String deadline;
    private String mlProcessingStatus;
    private String status; // PROCESSED / NOT_PROCESSED
}

