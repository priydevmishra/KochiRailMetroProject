package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class CloudStorageService {
    private final Storage storage;

    @Value("${google.cloud.storage.bucket}")
    private String bucketName;

    @Value("${google.cloud.storage.base-url}")
    private String baseUrl;

    public CloudStorageService(Storage storage) {
        this.storage = storage;
    }

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        String fileName = generateFileName(file.getOriginalFilename());
        String objectName = folder + "/" + fileName;

        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .build();

        Blob blob = storage.create(blobInfo, file.getBytes());

        return String.format("%s/%s/%s", baseUrl, bucketName, objectName);
    }

    public void deleteFile(String fileUrl) {
        try {
            String objectName = extractObjectNameFromUrl(fileUrl);
            BlobId blobId = BlobId.of(bucketName, objectName);
            storage.delete(blobId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from cloud storage", e);
        }
    }

    public byte[] downloadFile(String fileUrl) {
        try {
            String objectName = extractObjectNameFromUrl(fileUrl);
            BlobId blobId = BlobId.of(bucketName, objectName);
            Blob blob = storage.get(blobId);
            return blob.getContent();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from cloud storage", e);
        }
    }

    private String generateFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }

    private String extractObjectNameFromUrl(String fileUrl) {
        // Extract object name from URL: https://storage.googleapis.com/bucket/folder/file.ext
        String[] parts = fileUrl.split("/");
        StringBuilder objectName = new StringBuilder();
        boolean foundBucket = false;

        for (int i = 0; i < parts.length; i++) {
            if (foundBucket) {
                if (objectName.length() > 0) {
                    objectName.append("/");
                }
                objectName.append(parts[i]);
            } else if (parts[i].equals(bucketName)) {
                foundBucket = true;
            }
        }

        return objectName.toString();
    }
}
