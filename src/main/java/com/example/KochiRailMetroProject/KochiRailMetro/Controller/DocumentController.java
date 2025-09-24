package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentSearchDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.DocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/documents")  // Isa Controller kaa abhi use nhi hai, aage use hoga... iski saari api likh lena aur GPT se pooch lena, controller aur iski service layer daalke ki kyaa use hai...iski api ko test mat karna...
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<DocumentDto>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("source") Document.DocumentSource source,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "tags", required = false) Set<String> tags,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            DocumentDto document = documentService.uploadDocument(file, source, categoryId, tags, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true, "Document uploaded successfully", document));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "Failed to upload document: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<DocumentDto>>> searchDocuments(
            @RequestParam(value = "query", required = false) String searchTerm,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "source", required = false) Document.DocumentSource documentSource,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "direction", defaultValue = "DESC") String sortDirection,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        DocumentSearchDto searchDto = new DocumentSearchDto();
        searchDto.setSearchTerm(searchTerm);
        searchDto.setCategoryId(categoryId);
        searchDto.setDocumentSource(documentSource);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<DocumentDto> documents = documentService.searchDocuments(searchDto, currentUser, pageable);

        return ResponseEntity.ok(new ApiResponse<>(true, "Documents retrieved successfully", documents));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentDto>> getDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            DocumentDto document = documentService.getDocumentById(id, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true, "Document retrieved successfully", document));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage()));
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            byte[] fileData = documentService.downloadDocument(id, currentUser);
            DocumentDto document = documentService.getDocumentById(id, currentUser);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", document.getOriginalFilename());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        try {
            documentService.deleteDocument(id, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true, "Document deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage()));
        }
    }
}

