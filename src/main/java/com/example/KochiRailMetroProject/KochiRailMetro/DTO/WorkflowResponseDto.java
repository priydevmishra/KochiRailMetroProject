package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponseDto {
    private Long id; // workflow id
    private DocumentBasicDto document;
    private UserBasicDto assignedTo;
    private UserBasicDto createdBy;
    private String status; // PENDING, COMPLETED etc.
}
