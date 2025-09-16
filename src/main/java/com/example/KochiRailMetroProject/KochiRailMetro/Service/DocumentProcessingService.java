package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentContent;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentMetadata;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentRepository;
import jakarta.transaction.Transactional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

@Service
public class DocumentProcessingService {
    private final DocumentRepository documentRepository;
    private final LocalFileStorageService fileStorageService; // UPDATED
    private final MLService mlService;

    // ✅ Updated constructor
    public DocumentProcessingService(DocumentRepository documentRepository,
                                     LocalFileStorageService fileStorageService, // UPDATED
                                     MLService mlService) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService; // UPDATED
        this.mlService = mlService;
    }

    @Async
    @Transactional
    public CompletableFuture<Void> processDocumentAsync(Long documentId) {
        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found"));

            // Create document content record
            DocumentContent content = new DocumentContent();
            content.setDocument(document);
            content.setProcessingStatus(DocumentContent.ProcessingStatus.PROCESSING);

            // ✅ Download file from local storage instead of cloud
            byte[] fileData = fileStorageService.downloadFile(document.getCloudUrl());

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

            // Save content
            document.setContent(content);
            documentRepository.save(document);

        } catch (Exception e) {
            // Mark processing as failed
            Document document = documentRepository.findById(documentId).orElse(null);
            if (document != null && document.getContent() != null) {
                document.getContent().setProcessingStatus(DocumentContent.ProcessingStatus.FAILED);
                documentRepository.save(document);
            }
            throw new RuntimeException("Document processing failed", e);
        }
        return CompletableFuture.completedFuture(null);
    }

    private String extractTextFromFile(byte[] fileData, String mimeType) throws IOException {
        if (mimeType == null) {
            return "";
        }
        switch (mimeType.toLowerCase()) {
            case "application/pdf":
                return extractTextFromPdf(fileData);
            case "text/plain":
                return new String(fileData);
            case "application/json":
                return new String(fileData);
            default:
                return "";
        }
    }

    private String extractTextFromPdf(byte[] pdfData) throws IOException {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfData))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private void extractMetadata(Document document, byte[] fileData, String extractedText) {
        switch (document.getDocumentSource()) {
            case GMAIL:
                extractGmailMetadata(document, extractedText);
                break;
            case WHATSAPP:
                extractWhatsAppMetadata(document, extractedText);
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
