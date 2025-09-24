package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.GmailSyncStatusDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.*;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.AuditLogRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.GmailSyncHistoryRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.util.CustomMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GmailService {

    private static final Logger logger = LoggerFactory.getLogger(GmailService.class);
    private static final String APPLICATION_NAME = "KMRL Document Management";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);

    private static final Set<String> SUPPORTED_ATTACHMENT_TYPES = Set.of(
            "application/pdf", "text/plain", "text/csv",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/msword", "application/vnd.ms-excel",
            "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp"
    );

    @Value("${google.gmail.credentials.file}")
    private String credentialsFilePath;

    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final GmailSyncHistoryRepository syncHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GmailService(DocumentService documentService,
                        UserRepository userRepository,
                        AuditLogRepository auditLogRepository,
                        GmailSyncHistoryRepository syncHistoryRepository) {
        this.documentService = documentService;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.syncHistoryRepository = syncHistoryRepository;
    }

    /**
     * Main sync method - NO class-level transaction, handled per message
     */
    public List<DocumentDto> syncNewEmails(UserPrincipal currentUser) throws IOException, GeneralSecurityException {
        logger.info("Starting Gmail sync for user: {}", currentUser.getId());

        Gmail service = getGmailService();
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<DocumentDto> allSyncedDocs = new ArrayList<>();

        // Get all Gmail messages
        ListMessagesResponse response = service.users().messages()
                .list("me")
                .execute();

        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            logger.info("No messages found in Gmail for user: {}", currentUser.getId());
            return allSyncedDocs;
        }

        logger.info("Total Gmail messages found: {}", messages.size());

        // Get already synced message IDs
        Set<String> syncedMessageIds = syncHistoryRepository.findSyncedMessageIdsByUser(user);
        logger.info("User {} has {} already synced messages", currentUser.getId(), syncedMessageIds.size());

        // Filter new messages
        List<Message> newMessages = new ArrayList<>();
        for (Message message : messages) {
            if (!syncedMessageIds.contains(message.getId())) {
                newMessages.add(message);
            }
        }

        logger.info("Found {} NEW messages to sync for user: {}", newMessages.size(), currentUser.getId());

        if (newMessages.isEmpty()) {
            logger.info("No new messages to sync");
            return allSyncedDocs;
        }

        // Process each message individually with separate transactions
        int successCount = 0;
        for (Message message : newMessages) {
            try {
                List<DocumentDto> messageDocuments = processSingleEmailInTransaction(message.getId(), currentUser, user, service);
                allSyncedDocs.addAll(messageDocuments);
                successCount++;
                logger.info("Successfully processed message {}/{}: {}", successCount, newMessages.size(), message.getId());
            } catch (Exception e) {
                logger.error("Failed to process message {}: {}", message.getId(), e.getMessage(), e);
                // Continue with next message
            }
        }

        logger.info("Gmail sync completed. Processed {}/{} messages successfully, created {} documents",
                successCount, newMessages.size(), allSyncedDocs.size());

        return allSyncedDocs;
    }

    /**
     * Process single email in its own transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<DocumentDto> processSingleEmailInTransaction(String messageId, UserPrincipal currentUser, User user, Gmail service) {
        try {
            // Double-check if already synced (race condition protection)
            if (syncHistoryRepository.existsByGmailMessageIdAndUser(messageId, user)) {
                logger.debug("Message {} already synced for user {}, skipping", messageId, currentUser.getId());
                return Collections.emptyList();
            }

            // Get full message
            Message fullMessage = service.users().messages()
                    .get("me", messageId)
                    .setFormat("full")
                    .execute();

            // Process message and create documents
            List<DocumentDto> messageDocuments = processEmailMessage(fullMessage, currentUser, user, service);

            // Create sync history record (this MUST succeed for transaction to commit)
            createSyncHistoryRecord(fullMessage, user, messageDocuments);

            logger.debug("Successfully processed and tracked message: {}", messageId);
            return messageDocuments;

        } catch (IOException e) {
            logger.error("Gmail API error for message {}: {}", messageId, e.getMessage(), e);
            throw new RuntimeException("Gmail API error: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error in transaction for message {}: {}", messageId, e.getMessage(), e);
            throw new RuntimeException("Failed to process message: " + messageId, e);
        }
    }

    /**
     * Get unsynced count - READ ONLY
     */
    @Transactional(readOnly = true)
    public GmailSyncStatusDto getUnsyncedEmailsCount(UserPrincipal currentUser) throws IOException, GeneralSecurityException {
        logger.info("Checking unsynced emails count for user: {}", currentUser.getId());

        Gmail service = getGmailService();
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Get all Gmail messages
        ListMessagesResponse response = service.users().messages()
                .list("me")
                .execute();

        int totalEmailsInGmail = (response.getMessages() != null) ? response.getMessages().size() : 0;

        // Get synced message IDs for this user
        Set<String> syncedMessageIds = syncHistoryRepository.findSyncedMessageIdsByUser(user);
        int syncedCount = syncedMessageIds.size();

        // Calculate unsynced count
        int unsyncedCount = 0;
        if (response.getMessages() != null) {
            for (Message message : response.getMessages()) {
                if (!syncedMessageIds.contains(message.getId())) {
                    unsyncedCount++;
                }
            }
        }

        LocalDateTime lastSyncTime = syncHistoryRepository.findLastSyncTimeByUser(user).orElse(null);

        GmailSyncStatusDto status = new GmailSyncStatusDto();
        status.setUnsyncedEmailsCount(unsyncedCount);
        status.setLastSyncTime(lastSyncTime);
        status.setCheckedAt(LocalDateTime.now());
        status.setStatus(unsyncedCount > 0 ? "PENDING" : "UP_TO_DATE");
        status.setMessage(String.format("Total: %d emails, Synced: %d, Unsynced: %d",
                totalEmailsInGmail, syncedCount, unsyncedCount));

        logger.info("Sync status for user {}: {} total, {} synced, {} unsynced",
                currentUser.getId(), totalEmailsInGmail, syncedCount, unsyncedCount);

        return status;
    }

    /**
     * Process email message - NO transaction here
     */
    private List<DocumentDto> processEmailMessage(Message message, UserPrincipal currentUser, User user, Gmail service) throws IOException {
        List<DocumentDto> documents = new ArrayList<>();

        try {
            // Process email content
            DocumentDto emailDoc = processEmailAsDocument(message, currentUser);
            if (emailDoc != null) {
                documents.add(emailDoc);
                logger.debug("Created email document: {}", emailDoc.getFilename());
            }

            // Process attachments
            if (message.getPayload() != null && message.getPayload().getParts() != null) {
                String subject = getEmailSubject(message);
                String sender = getEmailSender(message);

                List<DocumentDto> attachmentDocs = processAttachmentParts(
                        currentUser, service, message,
                        message.getPayload().getParts(), subject, sender
                );
                documents.addAll(attachmentDocs);
                logger.debug("Created {} attachment documents", attachmentDocs.size());
            }

        } catch (Exception e) {
            logger.error("Error processing email message {}: {}", message.getId(), e.getMessage(), e);
            throw e; // Re-throw to fail the transaction
        }

        return documents;
    }

    /**
     * Create sync history - within existing transaction
     */
    private void createSyncHistoryRecord(Message message, User user, List<DocumentDto> documents) {
        try {
            // Check for existing record
            if (syncHistoryRepository.existsByGmailMessageIdAndUser(message.getId(), user)) {
                logger.warn("Sync history already exists for message {} and user {}", message.getId(), user.getId());
                return;
            }

            GmailSyncHistory syncHistory = new GmailSyncHistory();
            syncHistory.setGmailMessageId(message.getId());
            syncHistory.setUser(user);
            syncHistory.setEmailSubject(getEmailSubject(message));
            syncHistory.setEmailSender(getEmailSender(message));

            // Count attachments
            int attachmentCount = 0;
            if (message.getPayload() != null && message.getPayload().getParts() != null) {
                attachmentCount = (int) message.getPayload().getParts().stream()
                        .filter(part -> part.getFilename() != null && !part.getFilename().isEmpty())
                        .count();
            }

            syncHistory.setHasAttachments(attachmentCount > 0);
            syncHistory.setAttachmentsCount(attachmentCount);

            if (!documents.isEmpty()) {
                Document doc = new Document();
                doc.setId(documents.get(0).getId());
                syncHistory.setDocument(doc);
            }

            GmailSyncHistory saved = syncHistoryRepository.save(syncHistory);
            logger.debug("Created sync history record ID {} for message {}", saved.getId(), message.getId());

        } catch (Exception e) {
            logger.error("FAILED to create sync history for message {}: {}", message.getId(), e.getMessage(), e);
            throw new RuntimeException("Critical: Failed to create sync history record", e);
        }
    }

    // All other helper methods remain the same...
    private List<DocumentDto> processAttachmentParts(UserPrincipal currentUser, Gmail service, Message message,
                                                     List<MessagePart> parts, String subject, String sender) throws IOException {
        List<DocumentDto> attachmentDocs = new ArrayList<>();

        for (MessagePart part : parts) {
            try {
                if (part.getParts() != null && !part.getParts().isEmpty()) {
                    List<DocumentDto> nestedDocs = processAttachmentParts(currentUser, service, message, part.getParts(), subject, sender);
                    attachmentDocs.addAll(nestedDocs);
                } else {
                    DocumentDto attachmentDoc = processSingleAttachment(currentUser, service, message, part, subject, sender);
                    if (attachmentDoc != null) {
                        attachmentDocs.add(attachmentDoc);
                    }
                }
            } catch (Exception e) {
                logger.error("Error processing attachment part: {}", e.getMessage(), e);
            }
        }

        return attachmentDocs;
    }

    private DocumentDto processSingleAttachment(UserPrincipal currentUser, Gmail service, Message message,
                                                MessagePart part, String subject, String sender) {
        if (part.getFilename() == null || part.getFilename().isEmpty()) {
            return null;
        }

        String attachmentId = part.getBody() != null ? part.getBody().getAttachmentId() : null;
        if (attachmentId == null) {
            return null;
        }

        String mimeType = part.getMimeType();
        if (mimeType == null || !SUPPORTED_ATTACHMENT_TYPES.contains(mimeType)) {
            return null;
        }

        File tempFile = null;
        try {
            MessagePartBody attachment = service.users().messages()
                    .attachments()
                    .get("me", message.getId(), attachmentId)
                    .execute();

            if (attachment.getData() == null) {
                return null;
            }

            byte[] fileData = Base64.getUrlDecoder().decode(attachment.getData());
            if (fileData.length == 0) {
                return null;
            }

            String sanitizedFilename = sanitizeFilename(part.getFilename());
            tempFile = File.createTempFile("gmail_attach_", "_" + sanitizedFilename);

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(fileData);
            }

            MultipartFile multipartFile = new CustomMultipartFile(tempFile, sanitizedFilename, mimeType);
            DocumentDto doc = documentService.uploadDocument(
                    multipartFile,
                    Document.DocumentSource.GMAIL,
                    null,
                    Set.of("gmail", "attachment", "email-attachment"),
                    currentUser
            );

            userRepository.findById(currentUser.getId()).ifPresent(user ->
                    logEmailAudit(doc, subject, sender, "[Attachment] " + sanitizedFilename, user)
            );

            return doc;

        } catch (Exception e) {
            logger.error("Error processing attachment {}: {}", part.getFilename(), e.getMessage(), e);
            return null;
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                logger.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
            }
        }
    }

    private Gmail getGmailService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        try (InputStream in = new FileInputStream(credentialsFilePath)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();
            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8889).build();
            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }
    }

    private DocumentDto processEmailAsDocument(Message message, UserPrincipal currentUser) {
        File tempFile = null;
        try {
            String emailContent = getEmailContent(message);
            String subject = getEmailSubject(message);

            if (emailContent.trim().isEmpty()) {
                return null;
            }

            String sanitizedSubject = sanitizeFilename(subject);
            tempFile = File.createTempFile("email_", ".txt");

            try (FileWriter writer = new FileWriter(tempFile, StandardCharsets.UTF_8)) {
                writer.write("Subject: " + subject + "\n");
                writer.write("From: " + getEmailSender(message) + "\n");
                writer.write("Date: " + getEmailDate(message) + "\n");
                writer.write("Message ID: " + message.getId() + "\n");
                writer.write("\n--- Email Content ---\n\n");
                writer.write(emailContent);
            }

            MultipartFile emailFile = new CustomMultipartFile(tempFile, sanitizedSubject + ".txt", "text/plain");
            DocumentDto doc = documentService.uploadDocument(emailFile, Document.DocumentSource.GMAIL, null,
                    Set.of("email", "gmail", "text-content"), currentUser);

            userRepository.findById(currentUser.getId()).ifPresent(user ->
                    logEmailAudit(doc, subject, getEmailSender(message), "[Email Content] " + subject, user));

            return doc;

        } catch (Exception e) {
            logger.error("Error processing email as document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process email as document", e);
        } finally {
            if (tempFile != null && tempFile.exists() && !tempFile.delete()) {
                logger.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
            }
        }
    }

    private void logEmailAudit(DocumentDto documentDto, String subject, String sender, String body, User user) {
        try {
            Map<String, Object> detailsMap = new HashMap<>();
            detailsMap.put("subject", subject != null ? subject : "No Subject");
            detailsMap.put("from", sender != null ? sender : "Unknown Sender");
            detailsMap.put("bodyPreview", body != null ? (body.length() > 200 ? body.substring(0, 200) + "..." : body) : "");
            detailsMap.put("documentId", documentDto != null ? documentDto.getId() : null);
            detailsMap.put("filename", documentDto != null ? documentDto.getFilename() : null);

            String jsonDetails = objectMapper.writeValueAsString(detailsMap);
            AuditLog auditLog = new AuditLog();
            auditLog.setAction(AuditAction.EMAIL_SYNC);
            auditLog.setDetails(jsonDetails);

            if (documentDto != null) {
                Document doc = new Document();
                doc.setId(documentDto.getId());
                auditLog.setDocument(doc);
            }

            auditLog.setUser(user);
            auditLogRepository.save(auditLog);

        } catch (Exception e) {
            logger.error("Failed to log audit: {}", e.getMessage(), e);
        }
    }

    private String getEmailContent(Message message) {
        StringBuilder content = new StringBuilder();
        MessagePart payload = message.getPayload();
        if (payload == null) return "";

        try {
            if (payload.getBody() != null && payload.getBody().getData() != null) {
                byte[] data = Base64.getUrlDecoder().decode(payload.getBody().getData());
                content.append(new String(data, StandardCharsets.UTF_8));
            } else if (payload.getParts() != null) {
                for (MessagePart part : payload.getParts()) {
                    if ("text/plain".equals(part.getMimeType()) &&
                            part.getBody() != null && part.getBody().getData() != null) {
                        byte[] data = Base64.getUrlDecoder().decode(part.getBody().getData());
                        content.append(new String(data, StandardCharsets.UTF_8));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error decoding email content: {}", e.getMessage());
        }
        return content.toString();
    }

    private String getEmailSubject(Message message) {
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                if ("Subject".equals(header.getName())) {
                    return header.getValue() != null ? header.getValue() : "No Subject";
                }
            }
        }
        return "No Subject";
    }

    private String getEmailSender(Message message) {
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                if ("From".equals(header.getName())) {
                    return header.getValue() != null ? header.getValue() : "Unknown Sender";
                }
            }
        }
        return "Unknown Sender";
    }

    private String getEmailDate(Message message) {
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                if ("Date".equals(header.getName())) {
                    return header.getValue() != null ? header.getValue() : "Unknown Date";
                }
            }
        }
        return "Unknown Date";
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "untitled";
        }
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}