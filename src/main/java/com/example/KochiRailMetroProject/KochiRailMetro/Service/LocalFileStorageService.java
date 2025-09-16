// src/main/java/com/example/KochiRailMetroProject/KochiRailMetro/Service/LocalFileStorageService.java

package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class LocalFileStorageService {

    @Value("${app.file-storage.base-path:./uploads}")
    private String basePath;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        // Create folder structure
        String uploadDir = basePath + "/" + folder;
        Path dirPath = Paths.get(uploadDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        // Generate unique filename
        String fileName = generateUniqueFilename(file.getOriginalFilename());
        String filePath = uploadDir + "/" + fileName;

        // Save file to filesystem
        file.transferTo(new File(filePath));

        // Return local URL
        return baseUrl + "/api/files/" + folder + "/" + fileName;
    }

    public void deleteFile(String fileUrl) {
        try {
            String relativePath = extractRelativePathFromUrl(fileUrl);
            String fullPath = basePath + "/" + relativePath;

            File file = new File(fullPath);
            if (file.exists() && file.isFile()) {
                file.delete();
                System.out.println("File deleted: " + fullPath);
            }
        } catch (Exception e) {
            System.err.println("Failed to delete file: " + e.getMessage());
        }
    }

    public byte[] downloadFile(String fileUrl) throws IOException {
        try {
            String relativePath = extractRelativePathFromUrl(fileUrl);
            String fullPath = basePath + "/" + relativePath;

            Path filePath = Paths.get(fullPath);
            if (Files.exists(filePath)) {
                return Files.readAllBytes(filePath);
            } else {
                throw new IOException("File not found: " + fullPath);
            }
        } catch (Exception e) {
            throw new IOException("Failed to download file: " + e.getMessage());
        }
    }

    public boolean fileExists(String fileUrl) {
        try {
            String relativePath = extractRelativePathFromUrl(fileUrl);
            String fullPath = basePath + "/" + relativePath;
            return new File(fullPath).exists();
        } catch (Exception e) {
            return false;
        }
    }

    private String generateUniqueFilename(String originalFilename) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        if (originalFilename != null && originalFilename.contains(".")) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String nameWithoutExt = originalFilename.substring(0, originalFilename.lastIndexOf("."));
            return timestamp + "_" + uuid + "_" + sanitizeFilename(nameWithoutExt) + extension;
        }

        return timestamp + "_" + uuid + "_" + sanitizeFilename(originalFilename);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        // Remove special characters and replace with underscore
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private String extractRelativePathFromUrl(String fileUrl) {
        // Extract relative path from URL like: http://localhost:8080/api/files/gmail/filename.pdf
        // Return: gmail/filename.pdf

        String filesPath = "/api/files/";
        int index = fileUrl.indexOf(filesPath);
        if (index != -1) {
            return fileUrl.substring(index + filesPath.length());
        }
        throw new IllegalArgumentException("Invalid file URL format");
    }
}
