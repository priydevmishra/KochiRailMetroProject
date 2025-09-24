package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentSearchDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Category;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Tag;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.CategoryRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.TagRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final DocumentProcessingService documentProcessingService;
    private final AuditService auditService;
    private final CloudinaryService cloudinaryService;

    public DocumentService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           CategoryRepository categoryRepository,
                           TagRepository tagRepository,
                           DocumentProcessingService documentProcessingService,
                           AuditService auditService,
                           CloudinaryService cloudinaryService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.documentProcessingService = documentProcessingService;
        this.auditService = auditService;
        this.cloudinaryService = cloudinaryService;
    }

    // Upload document to Cloudinary
    public DocumentDto uploadDocument(MultipartFile file,
                                      Document.DocumentSource source,
                                      Long categoryId,
                                      Set<String> tagNames,
                                      UserPrincipal currentUser) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String folder = "kmrl/" + source.name().toLowerCase();
        CloudinaryService.CloudinaryResponse cloudinaryResponse = cloudinaryService.uploadFile(file, folder);

        Document document = new Document();
        document.setFilename(generateUniqueFilename(file.getOriginalFilename()));
        document.setOriginalFilename(file.getOriginalFilename());

        document.setCloudUrl(cloudinaryResponse.getSecureUrl());
        document.setCloudinaryPublicId(cloudinaryResponse.getPublicId());
        document.setCloudinarySecureUrl(cloudinaryResponse.getSecureUrl());
        document.setCloudinaryResourceType(cloudinaryResponse.getResourceType());
        document.setFileFormat(cloudinaryResponse.getFormat());

        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDocumentSource(source);
        document.setUploadedBy(user);
        document.setChecksum(calculateChecksum(file.getBytes()));

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            document.setCategory(category);
        }

        if (tagNames != null && !tagNames.isEmpty()) {
            Set<Tag> tags = tagNames.stream()
                    .map(this::findOrCreateTag)
                    .collect(Collectors.toSet());
            document.setTags(tags);
        }

        document = documentRepository.save(document);

        documentProcessingService.processDocumentAsync(document.getId());

        auditService.logAction(user.getId(), document.getId(), "DOCUMENT_UPLOADED",
                "Document uploaded to Cloudinary: " + file.getOriginalFilename());

        return convertToDto(document);
    }

    // Download document from Cloudinary
    public byte[] downloadDocument(Long id, UserPrincipal currentUser) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (document.getIsDeleted()) {
            throw new RuntimeException("Document has been deleted");
        }

        auditService.logAction(currentUser.getId(), document.getId(), "DOCUMENT_DOWNLOADED",
                "Document downloaded: " + document.getFilename());

        try {
            return downloadFromUrl(document.getCloudinarySecureUrl());
        } catch (IOException e) {
            throw new RuntimeException("Failed to download document from Cloudinary", e);
        }
    }

    private byte[] downloadFromUrl(String url) throws IOException {
        try (InputStream in = new URL(url).openStream()) {
            return in.readAllBytes();
        }
    }

    // For GmailService
    public Optional<Document> findById(Long id) {
        return documentRepository.findById(id);
    }

    // NEW: Search documents
    public Page<DocumentDto> searchDocuments(DocumentSearchDto searchDto, UserPrincipal currentUser, Pageable pageable) {
        Page<Document> docs;

        if (searchDto.getSearchTerm() != null && !searchDto.getSearchTerm().isEmpty()) {
            docs = documentRepository.findByFilenameContainingIgnoreCase(searchDto.getSearchTerm(), pageable);
        } else if (searchDto.getCategoryId() != null) {
            docs = documentRepository.findByCategory_Id(searchDto.getCategoryId(), pageable);
        } else if (searchDto.getDocumentSource() != null) {
            docs = documentRepository.findByDocumentSource(searchDto.getDocumentSource(), pageable);
        } else {
            docs = documentRepository.findAll(pageable);
        }

        return docs.map(this::convertToDto);
    }

    // NEW: Get document by ID
    public DocumentDto getDocumentById(Long id, UserPrincipal currentUser) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (document.getIsDeleted()) {
            throw new RuntimeException("Document has been deleted");
        }

        auditService.logAction(currentUser.getId(), document.getId(), "DOCUMENT_VIEWED",
                "Document viewed: " + document.getFilename());

        return convertToDto(document);
    }

    // NEW: Delete document
    public void deleteDocument(Long id, UserPrincipal currentUser) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        document.setIsDeleted(true);
        documentRepository.save(document);

        auditService.logAction(currentUser.getId(), document.getId(), "DOCUMENT_DELETED",
                "Document deleted: " + document.getFilename());
    }

    private String generateUniqueFilename(String originalFilename) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return timestamp + "_" + originalFilename.replaceAll("[^a-zA-Z0-9.]", "_");
    }

    private String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate checksum", e);
        }
    }

    private Tag findOrCreateTag(String tagName) {
        return tagRepository.findByName(tagName)
                .orElseGet(() -> {
                    Tag newTag = new Tag();
                    newTag.setName(tagName);
                    return tagRepository.save(newTag);
                });
    }

    // Convert entity → DTO
    private DocumentDto convertToDto(Document document) {
        DocumentDto dto = new DocumentDto();
        dto.setId(document.getId());
        dto.setFilename(document.getFilename());
        dto.setOriginalFilename(document.getOriginalFilename());
        dto.setFileSize(document.getFileSize());
        dto.setMimeType(document.getMimeType());
        dto.setDocumentSource(document.getDocumentSource());
        dto.setCreatedAt(document.getCreatedAt());
        dto.setUpdatedAt(document.getUpdatedAt());

        if (document.getCategory() != null) {
            dto.setCategoryName(document.getCategory().getName());
        }

        if (document.getUploadedBy() != null) {
            dto.setUploadedByName(document.getUploadedBy().getFullName());
        }

        if (document.getTags() != null) {
            dto.setTags(document.getTags().stream()
                    .map(Tag::getName)
                    .collect(Collectors.toSet()));
        }

        if (document.getContent() != null) {
            dto.setSummary(document.getContent().getSummary());
            dto.setMlSummary(document.getContent().getMlSummary());
            dto.setProcessingStatus(document.getContent().getProcessingStatus());
        }

        dto.setCloudinaryPublicId(document.getCloudinaryPublicId());
        dto.setCloudinarySecureUrl(document.getCloudinarySecureUrl());
        dto.setCloudinaryResourceType(document.getCloudinaryResourceType());
        dto.setFileFormat(document.getFileFormat());

        if (document.getCloudinaryPublicId() != null &&
                "image".equals(document.getCloudinaryResourceType())) {
            dto.setThumbnailUrl(cloudinaryService.getThumbnailUrl(document.getCloudinaryPublicId()));
        }

        return dto;
    }
}
