package com.example.KochiRailMetroProject.KochiRailMetro.Repository;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


//import java.lang.ScopedValue;
import java.time.LocalDateTime;

@Repository
public interface DocumentRepository extends JpaRepository<Document,Long> {
    @Query("SELECT d FROM Document d WHERE d.isDeleted = false")
    Page<Document> findAllActive(Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.uploadedBy = :user")
    Page<Document> findByUploadedBy(@Param("user") User user, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.documentSource = :source")
    Page<Document> findByDocumentSource(@Param("source") Document.DocumentSource source, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.category.id = :categoryId")
    Page<Document> findByCategoryId(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.isDeleted = false AND d.createdAt BETWEEN :startDate AND :endDate")
    Page<Document> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate,
                                   Pageable pageable);

    @Query("SELECT d FROM Document d JOIN d.content dc WHERE d.isDeleted = false AND " +
            "(LOWER(d.filename) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(dc.extractedText) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Document> searchDocuments(@Param("searchTerm") String searchTerm, Pageable pageable);

//    <T> ScopedValue<T> findById(Long id);
}
