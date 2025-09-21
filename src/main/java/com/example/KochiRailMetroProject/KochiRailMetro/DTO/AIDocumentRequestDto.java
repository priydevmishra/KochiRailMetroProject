package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIDocumentRequestDto {

    @JsonProperty("document_id")
    private String documentId;  // Changed to String to match your API

    @JsonProperty("name")
    private String name;

    @JsonProperty("source")
    private String source;

    @JsonProperty("cloudinary_url")
    private String cloudinaryUrl;
}