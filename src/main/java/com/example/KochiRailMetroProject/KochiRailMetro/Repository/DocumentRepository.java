package com.example.KochiRailMetroProject.KochiRailMetro.Repository;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // 🔹 All active (not deleted)
    @Query("SELECT d FROM Document d WHERE d.isDeleted = false")
    Page<Document> findAllActive(Pageable pageable);

    // 🔹 By uploader
    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.uploadedBy = :user")
    Page<Document> findByUploadedBy(@Param("user") User user, Pageable pageable);

    // 🔹 By source
    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.documentSource = :source")
    Page<Document> findByDocumentSource(@Param("source") Document.DocumentSource source, Pageable pageable);

    // 🔹 By category
    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.category.id = :categoryId")
    Page<Document> findByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    // 🔹 By date range
    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.createdAt BETWEEN :startDate AND :endDate")
    Page<Document> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate,
                                   Pageable pageable);

    // 🔹 Full-text like search (filename + extracted text)
    @Query("SELECT d FROM Document d JOIN d.content dc WHERE d.isDeleted = false AND " +
            "(LOWER(d.filename) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(dc.extractedText) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Document> searchDocuments(@Param("searchTerm") String searchTerm, Pageable pageable);

    // --- Spring Data derived queries (no need for @Query) ---

    // 🔹 Search only filename
    Page<Document> findByFilenameContainingIgnoreCase(String filename, Pageable pageable);

    // 🔹 Search by category (derived version)
    Page<Document> findByCategory_Id(Long categoryId, Pageable pageable);

    // 🔹 Combine search: filename + category
    Page<Document> findByFilenameContainingIgnoreCaseAndCategory_Id(String filename, Long categoryId, Pageable pageable);

    // 🔹 Combine search: filename + source
    Page<Document> findByFilenameContainingIgnoreCaseAndDocumentSource(String filename, Document.DocumentSource source, Pageable pageable);

    // 🔹 Combine search: category + source
    Page<Document> findByCategory_IdAndDocumentSource(Long categoryId, Document.DocumentSource source, Pageable pageable);

    // 🔹 Combine all three: filename + category + source
    Page<Document> findByFilenameContainingIgnoreCaseAndCategory_IdAndDocumentSource(
            String filename,
            Long categoryId,
            Document.DocumentSource source,
            Pageable pageable
    );
}
