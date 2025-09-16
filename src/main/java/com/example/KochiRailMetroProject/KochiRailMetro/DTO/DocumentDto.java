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

    public DocumentDto(Document document) {
        this.id = document.getId();
        this.filename = document.getFilename();
        this.originalFilename = document.getOriginalFilename();
        this.fileSize = document.getFileSize();
        this.mimeType = document.getMimeType();
        this.documentSource = document.getDocumentSource();
        this.categoryName = (document.getCategory() != null) ? document.getCategory().getName() : null;
        this.uploadedByName = (document.getUploadedBy() != null) ? document.getUploadedBy().getFullName() : null;
        this.createdAt = document.getCreatedAt();
        this.updatedAt = document.getUpdatedAt();
        this.tags = (document.getTags() != null)
                ? document.getTags().stream().map(tag -> tag.getName()).collect(Collectors.toSet())
                : null;
        this.summary = (document.getContent() != null) ? document.getContent().getSummary() : null;
        this.mlSummary = (document.getContent() != null) ? document.getContent().getMlSummary() : null;
        this.processingStatus = (document.getContent() != null) ? document.getContent().getProcessingStatus() : null;

    }
}
