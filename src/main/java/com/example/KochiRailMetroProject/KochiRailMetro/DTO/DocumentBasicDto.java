package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentBasicDto {
    private Long id;
    private String filename;
    private String originalFilename;
    private String cloudUrl;
    private DocumentContentDto content;
}
