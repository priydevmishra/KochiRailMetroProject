package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.GmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/gmail")
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> syncGmailEmails(
            @RequestParam(value = "maxResults", defaultValue = "50") int maxResults,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            List<DocumentDto> documents = gmailService.syncRecentEmails(currentUser, maxResults);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "Gmail emails synced successfully", documents));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to sync Gmail emails: " + e.getMessage()));
        }
    }

    @PostMapping("/sync-attachments")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> syncEmailAttachments(
            @RequestParam(value = "maxResults", defaultValue = "20") int maxResults,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            List<DocumentDto> documents = gmailService.syncEmailAttachments(currentUser, maxResults);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "Gmail attachments synced successfully", documents));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to sync Gmail attachments: " + e.getMessage()));
        }
    }
}
