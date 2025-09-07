package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.WhatsAppService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/whatsapp")
public class WhatsAppController {

    private final WhatsAppService whatsAppService;

    public WhatsAppController(WhatsAppService whatsAppService) {
        this.whatsAppService = whatsAppService;
    }

    @PostMapping("/upload-chat")
    public ResponseEntity<ApiResponse<DocumentDto>> uploadChatExport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "tags", required = false) Set<String> tags,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            DocumentDto document = whatsAppService.processChatExport(file, categoryId, tags, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "WhatsApp chat uploaded successfully", document));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to upload WhatsApp chat: " + e.getMessage()));
        }
    }

    @PostMapping("/upload-media")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> uploadMediaFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "tags", required = false) Set<String> tags,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            List<DocumentDto> documents = whatsAppService.processMediaFiles(files, categoryId, tags, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "WhatsApp media files uploaded successfully", documents));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to upload WhatsApp media: " + e.getMessage()));
        }
    }
}
