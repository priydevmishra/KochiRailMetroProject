package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentContentDto {
    private String extractedText;
    private String summary;
    private String mlSummary;
    private String department;
    private String priority;
    private String deadline; // ISO String
}
