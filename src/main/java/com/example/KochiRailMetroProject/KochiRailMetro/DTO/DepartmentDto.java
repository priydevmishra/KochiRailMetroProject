package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DepartmentDto {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String headEmail;
    private Boolean notificationEnabled;
    private LocalDateTime createdAt;
}
