package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.GmailSyncStatusDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.GmailService;

@RestController
@RequestMapping("/api/v1/gmail") // gmail injestion ke liye API hai..
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    // Single unified sync endpoint - syncs all new emails with attachments, is api se saare mail sync hoke DB me store ho jaayenge
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> syncNewEmails(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            List<DocumentDto> documents = gmailService.syncNewEmails(currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "Gmail sync completed successfully. " + documents.size() + " new items processed.", documents));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to sync Gmail emails: " + e.getMessage()));
        }
    }

    // Get count of new unsync emails, kitne unsync emails hain, uskaa count btaayega...
    @GetMapping("/unsynced-count")
    public ResponseEntity<ApiResponse<GmailSyncStatusDto>> getUnsyncedEmailsCount(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            GmailSyncStatusDto syncStatus = gmailService.getUnsyncedEmailsCount(currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "Unsynced emails count retrieved successfully", syncStatus));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to get unsynced count: " + e.getMessage()));
        }
    }
}