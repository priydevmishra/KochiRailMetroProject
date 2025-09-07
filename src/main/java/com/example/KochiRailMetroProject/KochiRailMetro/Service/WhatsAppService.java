package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WhatsAppService {

    private final DocumentService documentService;

    // WhatsApp message pattern: [date, time] Contact Name: message
    private static final Pattern WHATSAPP_MESSAGE_PATTERN =
            Pattern.compile("\\[(\\d{1,2}/\\d{1,2}/\\d{2,4}),\\s*(\\d{1,2}:\\d{2}:\\d{2})\\]\\s*([^:]+):\\s*(.*)");

    public WhatsAppService(DocumentService documentService) {
        this.documentService = documentService;
    }

    public DocumentDto processChatExport(MultipartFile file, Long categoryId,
                                         Set<String> tags, UserPrincipal currentUser) throws IOException {

        // Validate file type
        if (!isValidChatFile(file)) {
            throw new IllegalArgumentException("Invalid WhatsApp chat file format");
        }

        Set<String> chatTags = tags != null ? new HashSet<>(tags) : new HashSet<>();
        chatTags.addAll(Set.of("whatsapp", "chat", "export"));

        DocumentDto document = documentService.uploadDocument(
                file,
                Document.DocumentSource.WHATSAPP,
                categoryId,
                chatTags,
                currentUser
        );

        return document;
    }

    public List<DocumentDto> processMediaFiles(List<MultipartFile> files, Long categoryId,
                                               Set<String> tags, UserPrincipal currentUser) throws IOException {

        List<DocumentDto> processedDocuments = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                if (isValidMediaFile(file)) {
                    Set<String> mediaTags = tags != null ? new HashSet<>(tags) : new HashSet<>();
                    mediaTags.addAll(Set.of("whatsapp", "media"));

                    DocumentDto document = documentService.uploadDocument(
                            file,
                            Document.DocumentSource.WHATSAPP,
                            categoryId,
                            mediaTags,
                            currentUser
                    );

                    processedDocuments.add(document);
                }
            } catch (Exception e) {
                System.err.println("Error processing WhatsApp media file: " +
                        file.getOriginalFilename() + " - " + e.getMessage());
            }
        }

        return processedDocuments;
    }

    public WhatsAppChatAnalysis analyzeChatContent(String chatContent) {
        WhatsAppChatAnalysis analysis = new WhatsAppChatAnalysis();

        String[] lines = chatContent.split("\n");
        Map<String, Integer> participantCounts = new HashMap<>();
        List<String> mediaFiles = new ArrayList<>();
        int totalMessages = 0;

        for (String line : lines) {
            Matcher matcher = WHATSAPP_MESSAGE_PATTERN.matcher(line);
            if (matcher.matches()) {
                totalMessages++;
                String participant = matcher.group(3).trim();
                participantCounts.put(participant,
                        participantCounts.getOrDefault(participant, 0) + 1);

                String message = matcher.group(4).trim();
                if (isMediaMessage(message)) {
                    mediaFiles.add(message);
                }
            }
        }

        analysis.setTotalMessages(totalMessages);
        analysis.setParticipantCounts(participantCounts);
        analysis.setMediaFiles(mediaFiles);
        analysis.setDateRange(extractDateRange(chatContent));

        return analysis;
    }

    private boolean isValidChatFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }

        // Check file extension
        String extension = filename.toLowerCase().substring(filename.lastIndexOf(".") + 1);
        return "txt".equals(extension) || "zip".equals(extension);
    }

    private boolean isValidMediaFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            return false;
        }

        return contentType.startsWith("image/") ||
                contentType.startsWith("video/") ||
                contentType.startsWith("audio/") ||
                contentType.equals("application/pdf");
    }

    private boolean isMediaMessage(String message) {
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("<media omitted>") ||
                lowerMessage.contains("image omitted") ||
                lowerMessage.contains("video omitted") ||
                lowerMessage.contains("audio omitted") ||
                lowerMessage.contains("document omitted");
    }

    private String extractDateRange(String chatContent) {
        List<String> dates = new ArrayList<>();
        String[] lines = chatContent.split("\n");

        for (String line : lines) {
            Matcher matcher = WHATSAPP_MESSAGE_PATTERN.matcher(line);
            if (matcher.matches()) {
                dates.add(matcher.group(1)); // Extract date
            }
        }

        if (dates.isEmpty()) {
            return "Unknown";
        }

        String firstDate = dates.get(0);
        String lastDate = dates.get(dates.size() - 1);

        return firstDate.equals(lastDate) ? firstDate : firstDate + " to " + lastDate;
    }

    // Inner class for chat analysis
    public static class WhatsAppChatAnalysis {
        private int totalMessages;
        private Map<String, Integer> participantCounts;
        private List<String> mediaFiles;
        private String dateRange;

        // Getters and setters
        public int getTotalMessages() { return totalMessages; }
        public void setTotalMessages(int totalMessages) { this.totalMessages = totalMessages; }

        public Map<String, Integer> getParticipantCounts() { return participantCounts; }
        public void setParticipantCounts(Map<String, Integer> participantCounts) { this.participantCounts = participantCounts; }

        public List<String> getMediaFiles() { return mediaFiles; }
        public void setMediaFiles(List<String> mediaFiles) { this.mediaFiles = mediaFiles; }

        public String getDateRange() { return dateRange; }
        public void setDateRange(String dateRange) { this.dateRange = dateRange; }
    }
}
