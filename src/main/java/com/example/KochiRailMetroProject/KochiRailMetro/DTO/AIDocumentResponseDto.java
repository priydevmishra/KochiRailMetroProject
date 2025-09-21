package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIDocumentResponseDto {

    @JsonProperty("document_id")
    private String documentId;  // Changed to match your response format

    @JsonProperty("department")
    private String department;

    @JsonProperty("priority")
    private String priority;

    @JsonProperty("deadline")
    private String deadline;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("error")
    private String error;
}