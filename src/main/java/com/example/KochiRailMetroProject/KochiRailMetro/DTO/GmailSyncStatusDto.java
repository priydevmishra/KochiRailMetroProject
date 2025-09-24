package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GmailSyncStatusDto {
    private int unsyncedEmailsCount;
    private LocalDateTime lastSyncTime;
    private LocalDateTime checkedAt;
    private String status;
    private String message;
}