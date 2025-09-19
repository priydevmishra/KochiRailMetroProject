package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentContent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentDto {
    private Long id;
    private String filename;
    private String originalFilename;
    private Long fileSize;
    private String mimeType;
    private Document.DocumentSource documentSource;
    private String categoryName;
    private String uploadedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<String> tags;
    private String summary;
    private String mlSummary;
    private DocumentContent.ProcessingStatus processingStatus;
    private String title;
    private String description;

    // 🔹 New Cloudinary fields
    private String cloudinaryPublicId;
    private String cloudinarySecureUrl;
    private String cloudinaryResourceType;
    private String fileFormat;
    private String thumbnailUrl;
}