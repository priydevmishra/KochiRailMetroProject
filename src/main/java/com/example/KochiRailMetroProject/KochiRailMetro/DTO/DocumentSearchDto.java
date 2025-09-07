package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class DocumentSearchDto {
    private String searchTerm;
    private Long categoryId;
    private Document.DocumentSource documentSource;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Set<String> tags;
}
