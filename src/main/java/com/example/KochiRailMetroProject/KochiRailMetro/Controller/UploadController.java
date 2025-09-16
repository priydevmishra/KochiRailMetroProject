package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.Service.CloudStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class UploadController {

    private final CloudStorageService cloudStorageService;

    public UploadController(CloudStorageService cloudStorageService) {
        this.cloudStorageService = cloudStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestPart("file") MultipartFile file,
                                    @RequestParam(value = "source", defaultValue = "general") String source) {
        try {
            // Use 'source' as folder name, e.g., gmail, whatsapp
            String fileUrl = cloudStorageService.uploadFile(file, source.toLowerCase());
            return ResponseEntity.ok(Map.of("success", true, "url", fileUrl));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}

