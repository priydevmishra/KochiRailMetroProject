package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.GmailInboxDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.GmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/gmail")
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    // Sync only text emails (no attachments)
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> syncGmailEmails(
            @RequestParam(value = "maxResults", defaultValue = "10") int maxResults,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            List<DocumentDto> documents = gmailService.syncEmailsOnly(currentUser, maxResults);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "Gmail emails synced successfully. " + documents.size() + " emails processed.", documents));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to sync Gmail emails: " + e.getMessage()));
        }
    }

    // Sync only attachments from emails (including email text + all attachments)
    @PostMapping("/sync-attachments")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> syncEmailAttachments(
            @RequestParam(value = "maxResults", defaultValue = "10") int maxResults,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            List<DocumentDto> documents = gmailService.syncEmailsWithAttachments(currentUser, maxResults);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "Gmail emails with attachments synced successfully. " + documents.size() + " items processed.", documents));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to sync Gmail attachments: " + e.getMessage()));
        }
    }

    // Fetch inbox information without syncing
    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<GmailInboxDto>> getInboxInfo(
            @RequestParam(value = "maxResults", defaultValue = "10") int maxResults,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            GmailInboxDto inboxInfo = gmailService.getInboxInfo(currentUser, maxResults);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "Inbox information fetched successfully", inboxInfo));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to fetch inbox: " + e.getMessage()));
        }
    }

    // Get unread message count
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        try {
            Map<String, Object> unreadInfo = gmailService.getUnreadMessageCount(currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "Unread count fetched successfully", unreadInfo));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to fetch unread count: " + e.getMessage()));
        }
    }
}