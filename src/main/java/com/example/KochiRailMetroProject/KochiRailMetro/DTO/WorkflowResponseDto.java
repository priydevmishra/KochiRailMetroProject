package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for workflow API
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponseDto {
    private Long id;                   // workflow id
    private DocumentBasicDto document; // document details
    private UserBasicDto assignedTo;   // manager/employee assigned
    private UserBasicDto assignedBy;   // jisne assign kiya (Admin/Manager)
    private UserBasicDto createdBy;    // jisne workflow create kiya
    private String status;             // workflow status (PENDING, COMPLETED, etc.)
}
