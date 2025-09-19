// src/main/java/com/example/KochiRailMetroProject/KochiRailMetro/Service/CloudinaryService.java

package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.cloudinary.Transformation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(@Value("${cloudinary.cloud-name}") String cloudName,
                             @Value("${cloudinary.api-key}") String apiKey,
                             @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    public CloudinaryResponse uploadFile(MultipartFile file, String folder) throws IOException {
        String publicId = generatePublicId(file.getOriginalFilename(), folder);

        Map<String, Object> uploadParams = ObjectUtils.asMap(
                "public_id", publicId,
                "folder", folder,
                "resource_type", "auto", // Handles images, videos, and raw files
                "overwrite", true,
                "notification_url", null
        );

        // Add specific parameters based on file type
        String fileType = getFileType(file.getContentType());
        if ("document".equals(fileType)) {
            uploadParams.put("resource_type", "raw");
        }

        Map<String, Object> result = cloudinary.uploader().upload(file.getBytes(), uploadParams);

        return new CloudinaryResponse(
                (String) result.get("public_id"),
                (String) result.get("secure_url"),
                (String) result.get("url"),
                (Integer) result.get("bytes"),
                (String) result.get("format"),
                (String) result.get("resource_type")
        );
    }

    public boolean deleteFile(String publicId) {
        try {
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            return "ok".equals(result.get("result"));
        } catch (Exception e) {
            System.err.println("Failed to delete file from Cloudinary: " + e.getMessage());
            return false;
        }
    }

    public String getOptimizedUrl(String publicId, int width, int height, String quality) {
        return cloudinary.url()
                .transformation(new Transformation()
                        .width(width)
                        .height(height)
                        .quality(quality)
                        .crop("fill"))
                .generate(publicId);
    }

    public String getThumbnailUrl(String publicId) {
        return cloudinary.url()
                .transformation(new Transformation()
                        .width(300)
                        .height(200)
                        .crop("fill")
                        .quality("auto"))
                .generate(publicId);
    }


    private String generatePublicId(String originalFilename, String folder) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String timestamp = String.valueOf(System.currentTimeMillis());

        if (originalFilename != null && originalFilename.contains(".")) {
            String nameWithoutExt = originalFilename.substring(0, originalFilename.lastIndexOf("."));
            return folder + "/" + timestamp + "_" + uuid + "_" + sanitizeFilename(nameWithoutExt);
        }

        return folder + "/" + timestamp + "_" + uuid + "_" + "file";
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "file";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String getFileType(String mimeType) {
        if (mimeType == null) return "document";

        if (mimeType.startsWith("image/")) return "image";
        if (mimeType.startsWith("video/")) return "video";
        if (mimeType.startsWith("audio/")) return "audio";
        return "document";
    }

    // Response class
    public static class CloudinaryResponse {
        private final String publicId;
        private final String secureUrl;
        private final String url;
        private final Integer bytes;
        private final String format;
        private final String resourceType;

        public CloudinaryResponse(String publicId, String secureUrl, String url,
                                  Integer bytes, String format, String resourceType) {
            this.publicId = publicId;
            this.secureUrl = secureUrl;
            this.url = url;
            this.bytes = bytes;
            this.format = format;
            this.resourceType = resourceType;
        }

        // Getters
        public String getPublicId() { return publicId; }
        public String getSecureUrl() { return secureUrl; }
        public String getUrl() { return url; }
        public Integer getBytes() { return bytes; }
        public String getFormat() { return format; }
        public String getResourceType() { return resourceType; }
    }
}