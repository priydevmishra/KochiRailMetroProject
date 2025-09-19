// src/main/java/com/example/KochiRailMetroProject/KochiRailMetro/Controller/CloudinaryController.java

package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.CloudinaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cloudinary")
public class CloudinaryController {

    private final CloudinaryService cloudinaryService;

    public CloudinaryController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder) {

        try {
            CloudinaryService.CloudinaryResponse response = cloudinaryService.uploadFile(file, folder);

            Map<String, Object> result = new HashMap<>();
            result.put("publicId", response.getPublicId());
            result.put("secureUrl", response.getSecureUrl());
            result.put("url", response.getUrl());
            result.put("bytes", response.getBytes());
            result.put("format", response.getFormat());
            result.put("resourceType", response.getResourceType());

            return ResponseEntity.ok(new ApiResponse<>(true, "File uploaded successfully", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to upload file: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable String publicId) {
        try {
            boolean deleted = cloudinaryService.deleteFile(publicId);
            if (deleted) {
                return ResponseEntity.ok(new ApiResponse<>(true, "File deleted successfully"));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Failed to delete file"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Error deleting file: " + e.getMessage()));
        }
    }
}