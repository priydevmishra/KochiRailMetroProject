package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentContent;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentMetadata;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentRepository;

import jakarta.transaction.Transactional;

@Service
public class DocumentProcessingService {
    private final DocumentRepository documentRepository;
    private final MLService mlService;

    // Removed LocalFileStorageService
    public DocumentProcessingService(DocumentRepository documentRepository,
                                     MLService mlService) {
        this.documentRepository = documentRepository;
        this.mlService = mlService;
    }

    @Async
    @Transactional
    public CompletableFuture<Void> processDocumentAsync(Long documentId) {
        Document document = null;
        try {
            document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found"));

            // Create/attach document content record early to persist PROCESSING state
            DocumentContent content = new DocumentContent();
            content.setDocument(document);
            content.setProcessingStatus(DocumentContent.ProcessingStatus.PROCESSING);
            document.setContent(content);
            documentRepository.save(document);

            // Validate Cloudinary URL before download
            String secureUrl = document.getCloudinarySecureUrl();
            if (secureUrl == null || secureUrl.isBlank()) {
                throw new IllegalArgumentException("Missing Cloudinary URL for document " + documentId);
            }

            // Download file from Cloudinary
            byte[] fileData = downloadFromCloudinary(secureUrl);

            // Extract text based on file type
            String extractedText = extractTextFromFile(fileData, document.getMimeType());
            content.setExtractedText(extractedText);

            // Extract metadata based on document source
            extractMetadata(document, fileData, extractedText);

            // Generate summary using ML service
            if (extractedText != null && !extractedText.trim().isEmpty()) {
                String mlSummary = mlService.generateSummary(extractedText);
                content.setMlSummary(mlSummary);
                content.setConfidenceScore(BigDecimal.valueOf(0.85)); // Default confidence
            }

            content.setProcessingStatus(DocumentContent.ProcessingStatus.COMPLETED);
            content.setProcessedAt(LocalDateTime.now());

            // Save updates
            documentRepository.save(document);

        } catch (Exception e) {
            // Mark processing as failed with best-effort persistence
            if (document == null) {
                document = documentRepository.findById(documentId).orElse(null);
            }
            if (document != null) {
                DocumentContent dc = document.getContent();
                if (dc == null) {
                    dc = new DocumentContent();
                    dc.setDocument(document);
                    document.setContent(dc);
                }
                dc.setProcessingStatus(DocumentContent.ProcessingStatus.FAILED);
                dc.setProcessedAt(LocalDateTime.now());
                try {
                    documentRepository.save(document);
                } catch (Exception ignored) {
                    // swallow to not mask original error
                }
            }
            throw new RuntimeException("Document processing failed", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    // Cloudinary file downloader
    private byte[] downloadFromCloudinary(String cloudinaryUrl) throws IOException {
        try (InputStream in = new URL(cloudinaryUrl).openStream()) {
            return in.readAllBytes();
        }
    }

    private String extractTextFromFile(byte[] fileData, String mimeType) throws IOException {
        if (mimeType == null) {
            return "";
        }
        switch (mimeType.toLowerCase()) {
            case "application/pdf":
                return extractTextFromPdf(fileData);
            case "text/plain":
            case "application/json":
                return new String(fileData, StandardCharsets.UTF_8);
            default:
                return "";
        }
    }

    private String extractTextFromPdf(byte[] pdfData) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfData)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private void extractMetadata(Document document, byte[] fileData, String extractedText) {
        if (document.getDocumentSource() == null) {
            return;
        }
        switch (document.getDocumentSource()) {
            case GMAIL:
                extractGmailMetadata(document, extractedText);
                break;
            case WHATSAPP:
                extractWhatsAppMetadata(document, extractedText);
                break;
            default:
                // Unknown or unsupported source
                break;
        }
    }

    private void extractGmailMetadata(Document document, String content) {
        if (content != null) {
            if (content.contains("From:")) {
                String fromLine = extractLine(content, "From:");
                addMetadata(document, "sender", fromLine);
            }
            if (content.contains("Subject:")) {
                String subjectLine = extractLine(content, "Subject:");
                addMetadata(document, "subject", subjectLine);
            }
            if (content.contains("Date:")) {
                String dateLine = extractLine(content, "Date:");
                addMetadata(document, "email_date", dateLine);
            }
        }
    }

    private void extractWhatsAppMetadata(Document document, String content) {
        if (content != null) {
            if (content.contains("WhatsApp Chat")) {
                addMetadata(document, "chat_type", "WhatsApp");
            }
            long messageCount = content.lines()
                    .filter(line -> line.matches(".*\\d{1,2}/\\d{1,2}/\\d{2,4}.*"))
                    .count();
            addMetadata(document, "message_count", String.valueOf(messageCount));
        }
    }

    private String extractLine(String content, String prefix) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith(prefix)) {
                return line.trim().substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private void addMetadata(Document document, String key, String value) {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setDocument(document);
        metadata.setKey(key);
        metadata.setValue(value);
        metadata.setDataType("string");
        if (document.getMetadata() == null) {
            document.setMetadata(new HashSet<>());
        }
        document.getMetadata().add(metadata);
    }
}
