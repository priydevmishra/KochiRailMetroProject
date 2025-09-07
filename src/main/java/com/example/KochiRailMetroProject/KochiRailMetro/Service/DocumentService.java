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
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final CloudStorageService cloudStorageService;
    private final DocumentProcessingService documentProcessingService;
    private final AuditService auditService;

    public DocumentService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           CategoryRepository categoryRepository,
                           TagRepository tagRepository,
                           CloudStorageService cloudStorageService,
                           DocumentProcessingService documentProcessingService,
                           AuditService auditService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.cloudStorageService = cloudStorageService;
        this.documentProcessingService = documentProcessingService;
        this.auditService = auditService;
    }

    public DocumentDto uploadDocument(MultipartFile file,
                                      Document.DocumentSource source,
                                      Long categoryId,
                                      Set<String> tagNames,
                                      UserPrincipal currentUser) throws IOException {

        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Get user
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Upload to cloud storage
        String folder = source.name().toLowerCase();
        String cloudUrl = cloudStorageService.uploadFile(file, folder);

        // Create document entity
        Document document = new Document();
        document.setFilename(generateUniqueFilename(file.getOriginalFilename()));
        document.setOriginalFilename(file.getOriginalFilename());
        document.setCloudUrl(cloudUrl);
        document.setFileSize(file.getSize());
        document.setMimeType(file.getContentType());
        document.setDocumentSource(source);
        document.setUploadedBy(user);
        document.setChecksum(calculateChecksum(file.getBytes()));

        // Set category if provided
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            document.setCategory(category);
        }

        // Process tags
        if (tagNames != null && !tagNames.isEmpty()) {
            Set<Tag> tags = tagNames.stream()
                    .map(this::findOrCreateTag)
                    .collect(Collectors.toSet());
            document.setTags(tags);
        }

        // Save document
        document = documentRepository.save(document);

        // Trigger async processing
        documentProcessingService.processDocumentAsync(document.getId());

        // Log audit
        auditService.logAction(user.getId(), document.getId(), "DOCUMENT_UPLOADED",
                "Document uploaded: " + file.getOriginalFilename());

        return convertToDto(document);
    }

    public Page<DocumentDto> searchDocuments(DocumentSearchDto searchDto,
                                             UserPrincipal currentUser,
                                             Pageable pageable) {

        Page<Document> documents;

        if (searchDto.getSearchTerm() != null && !searchDto.getSearchTerm().trim().isEmpty()) {
            documents = documentRepository.searchDocuments(searchDto.getSearchTerm(), pageable);
        } else if (searchDto.getCategoryId() != null) {
            documents = documentRepository.findByCategoryId(searchDto.getCategoryId(), pageable);
        } else if (searchDto.getDocumentSource() != null) {
            documents = documentRepository.findByDocumentSource(searchDto.getDocumentSource(), pageable);
        } else if (searchDto.getStartDate() != null && searchDto.getEndDate() != null) {
            documents = documentRepository.findByDateRange(searchDto.getStartDate(),
                    searchDto.getEndDate(), pageable);
        } else {
            documents = documentRepository.findAllActive(pageable);
        }

        return documents.map(this::convertToDto);
    }

    public DocumentDto getDocumentById(Long id, UserPrincipal currentUser) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (document.getIsDeleted()) {
            throw new RuntimeException("Document has been deleted");
        }

        // Log access
        auditService.logAction(currentUser.getId(), document.getId(), "DOCUMENT_ACCESSED",
                "Document accessed: " + document.getFilename());

        return convertToDto(document);
    }

    public void deleteDocument(Long id, UserPrincipal currentUser) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        // Soft delete
        document.setIsDeleted(true);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);

        // Log audit
        auditService.logAction(currentUser.getId(), document.getId(), "DOCUMENT_DELETED",
                "Document deleted: " + document.getFilename());
    }

    public byte[] downloadDocument(Long id, UserPrincipal currentUser) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        if (document.getIsDeleted()) {
            throw new RuntimeException("Document has been deleted");
        }

        // Log download
        auditService.logAction(currentUser.getId(), document.getId(), "DOCUMENT_DOWNLOADED",
                "Document downloaded: " + document.getFilename());

        return cloudStorageService.downloadFile(document.getCloudUrl());
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

        return dto;
    }
}
