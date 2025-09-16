//package com.example.KochiRailMetroProject.KochiRailMetro.Service;
//
//import com.google.cloud.storage.Blob;
//import com.google.cloud.storage.BlobId;
//import com.google.cloud.storage.BlobInfo;
//import com.google.cloud.storage.Storage;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//import java.util.UUID;
//
//@Service
//public class CloudStorageService {
//    private final Storage storage;
//
//    @Value("${google.cloud.storage.bucket}")
//    private String bucketName;
//
//    @Value("${google.cloud.storage.base-url}")
//    private String baseUrl;
//
//    public CloudStorageService(Storage storage) {
//        this.storage = storage;
//    }
//
//    public String uploadFile(MultipartFile file, String folder) throws IOException {
//        String fileName = generateFileName(file.getOriginalFilename());
//        String objectName = folder + "/" + fileName;
//
//        BlobId blobId = BlobId.of(bucketName, objectName);
//        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
//                .setContentType(file.getContentType())
//                .build();
//
//        Blob blob = storage.create(blobInfo, file.getBytes());
//
//        return String.format("%s/%s/%s", baseUrl, bucketName, objectName);
//    }
//
//    public void deleteFile(String fileUrl) {
//        try {
//            String objectName = extractObjectNameFromUrl(fileUrl);
//            BlobId blobId = BlobId.of(bucketName, objectName);
//            storage.delete(blobId);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to delete file from cloud storage", e);
//        }
//    }
//
//    public byte[] downloadFile(String fileUrl) {
//        try {
//            String objectName = extractObjectNameFromUrl(fileUrl);
//            BlobId blobId = BlobId.of(bucketName, objectName);
//            Blob blob = storage.get(blobId);
//            return blob.getContent();
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to download file from cloud storage", e);
//        }
//    }
//
//    private String generateFileName(String originalFilename) {
//        String extension = "";
//        if (originalFilename != null && originalFilename.contains(".")) {
//            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
//        }
//        return UUID.randomUUID().toString() + extension;
//    }
//
//    private String extractObjectNameFromUrl(String fileUrl) {
//        // Extract object name from URL: https://storage.googleapis.com/bucket/folder/file.ext
//        String[] parts = fileUrl.split("/");
//        StringBuilder objectName = new StringBuilder();
//        boolean foundBucket = false;
//
//        for (int i = 0; i < parts.length; i++) {
//            if (foundBucket) {
//                if (objectName.length() > 0) {
//                    objectName.append("/");
//                }
//                objectName.append(parts[i]);
//            } else if (parts[i].equals(bucketName)) {
//                foundBucket = true;
//            }
//        }
//
//        return objectName.toString();
//    }
//}

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
public class CloudStorageService {

    @Value("${google.cloud.storage.local-mode:true}")
    private boolean localMode;

    @Value("${google.cloud.storage.local-path:./uploads}")
    private String localStoragePath;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Upload file (local or cloud based on mode)
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        if (localMode) {
            return uploadToLocalStorage(file, folder);
        } else {
            // When you enable GCS later, add code here to upload using the injected Storage bean.
            throw new RuntimeException("Google Cloud Storage not configured for production use");
        }
    }

    /**
     * Upload to local storage
     */
    private String uploadToLocalStorage(MultipartFile file, String folder) throws IOException {
        // Create upload directory if it doesn't exist
        String uploadDir = localStoragePath + "/" + folder;
        Path dirPath = Paths.get(uploadDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        // Generate unique filename
        String original = file.getOriginalFilename();
        String safeOriginal = (original == null) ? "file" : original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = UUID.randomUUID().toString() + "_" + safeOriginal;
        String filePath = uploadDir + "/" + fileName;

        // Save file to local disk
        file.transferTo(new File(filePath));

        // Return file access URL
        return baseUrl + "/files/" + folder + "/" + fileName; // note: FileController mapping below serves /files/**
    }

    /**
     * Delete file
     */
    public void deleteFile(String fileUrl) {
        if (localMode) {
            deleteLocalFile(fileUrl);
        } else {
            throw new RuntimeException("Google Cloud Storage not configured");
        }
    }

    private void deleteLocalFile(String fileUrl) {
        try {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            String folder = fileUrl.split("/files/")[1].split("/")[0];
            String filePath = localStoragePath + "/" + folder + "/" + fileName;

            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            System.err.println("Failed to delete local file: " + e.getMessage());
        }
    }

    /**
     * Download file
     */
    public byte[] downloadFile(String fileUrl) {
        if (localMode) {
            return downloadLocalFile(fileUrl);
        } else {
            throw new RuntimeException("Google Cloud Storage not configured");
        }
    }

    private byte[] downloadLocalFile(String fileUrl) {
        try {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            String folder = fileUrl.split("/files/")[1].split("/")[0];
            String filePath = localStoragePath + "/" + folder + "/" + fileName;

            return Files.readAllBytes(Paths.get(filePath));
        } catch (Exception e) {
            throw new RuntimeException("Failed to download local file: " + e.getMessage());
        }
    }
}
