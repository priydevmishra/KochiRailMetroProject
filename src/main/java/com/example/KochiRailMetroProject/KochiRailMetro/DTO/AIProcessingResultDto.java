package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIProcessingResultDto {
    private Long documentId;
    private String department;
    private String priority;
    private LocalDate deadline;
    private String summary;
    private String mlProcessingStatus;
}