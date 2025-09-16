package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.GmailInboxDto;
import com.example.KochiRailMetroProject.KochiRailMetro.DTO.EmailInfoDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.*;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.AuditLogRepository;
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
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class GmailService {

    private static final Logger logger = LoggerFactory.getLogger(GmailService.class);
    private static final String APPLICATION_NAME = "KMRL Document Management";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);

    // Supported attachment file types
    private static final Set<String> SUPPORTED_ATTACHMENT_TYPES = Set.of(
            "application/pdf", "text/plain", "text/csv",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
            "application/msword", // .doc
            "application/vnd.ms-excel", // .xls
            "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp"
    );

    @Value("${google.gmail.credentials.file}")
    private String credentialsFilePath;

    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final LocalFileStorageService fileStorageService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GmailService(DocumentService documentService,
                        UserRepository userRepository,
                        LocalFileStorageService fileStorageService,
                        AuditLogRepository auditLogRepository) {
        this.documentService = documentService;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.auditLogRepository = auditLogRepository;
    }

    // ----------------- Sync only text emails (no attachments) -----------------
    public List<DocumentDto> syncEmailsOnly(UserPrincipal currentUser, int maxResults)
            throws IOException, GeneralSecurityException {

        logger.info("Starting email-only sync for user: {}, maxResults: {}", currentUser.getId(), maxResults);
        Gmail service = getGmailService();
        List<DocumentDto> syncedDocs = new ArrayList<>();

        ListMessagesResponse response = service.users().messages()
                .list("me")
                .setMaxResults((long) maxResults)
                .execute();

        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            logger.info("No messages found for user: {}", currentUser.getId());
            return syncedDocs;
        }

        for (Message message : messages) {
            try {
                Message fullMessage = service.users().messages()
                        .get("me", message.getId())
                        .setFormat("full")
                        .execute();

                // Only process email text, skip attachments
                DocumentDto emailDoc = processEmailAsDocument(fullMessage, currentUser);
                if (emailDoc != null) {
                    syncedDocs.add(emailDoc);
                }
            } catch (Exception e) {
                logger.error("Error processing email {}: {}", message.getId(), e.getMessage());
            }
        }

        logger.info("Email-only sync completed for user: {}, processed: {} emails",
                currentUser.getId(), syncedDocs.size());
        return syncedDocs;
    }

    // ----------------- Sync emails with all attachments -----------------
    public List<DocumentDto> syncEmailsWithAttachments(UserPrincipal currentUser, int maxResults)
            throws IOException, GeneralSecurityException {

        logger.info("Starting email + attachments sync for user: {}, maxResults: {}",
                currentUser.getId(), maxResults);
        Gmail service = getGmailService();
        List<DocumentDto> syncedDocs = new ArrayList<>();

        ListMessagesResponse response = service.users().messages()
                .list("me")
                .setMaxResults((long) maxResults)
                .execute();

        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            logger.info("No messages found for user: {}", currentUser.getId());
            return syncedDocs;
        }

        for (Message message : messages) {
            try {
                Message fullMessage = service.users().messages()
                        .get("me", message.getId())
                        .setFormat("full")
                        .execute();

                // Save email text as document
                DocumentDto emailTextDoc = processEmailAsDocument(fullMessage, currentUser);
                if (emailTextDoc != null) {
                    syncedDocs.add(emailTextDoc);
                }

                // Save attachments recursively if they exist
                if (fullMessage.getPayload() != null && fullMessage.getPayload().getParts() != null) {
                    String subject = getEmailSubject(fullMessage);
                    String sender = getEmailSender(fullMessage);

                    processAttachmentParts(currentUser, service, fullMessage,
                            syncedDocs, fullMessage.getPayload().getParts(), subject, sender);
                }
            } catch (Exception e) {
                logger.error("Error processing email with attachments {}: {}", message.getId(), e.getMessage());
            }
        }

        logger.info("Email + attachments sync completed for user: {}, processed: {} items",
                currentUser.getId(), syncedDocs.size());
        return syncedDocs;
    }

    // ----------------- Get inbox info without syncing -----------------
    public GmailInboxDto getInboxInfo(UserPrincipal currentUser, int maxResults)
            throws IOException, GeneralSecurityException {

        logger.info("Fetching inbox info for user: {}, maxResults: {}", currentUser.getId(), maxResults);
        Gmail service = getGmailService();

        ListMessagesResponse response = service.users().messages()
                .list("me")
                .setMaxResults((long) maxResults)
                .execute();

        List<Message> messages = response.getMessages();
        List<EmailInfoDto> emailInfoList = new ArrayList<>();

        if (messages != null && !messages.isEmpty()) {
            for (Message message : messages) {
                try {
                    Message fullMessage = service.users().messages()
                            .get("me", message.getId())
                            .setFormat("metadata")
                            .setMetadataHeaders(Arrays.asList("Subject", "From", "Date"))
                            .execute();

                    EmailInfoDto emailInfo = createEmailInfoDto(fullMessage);
                    emailInfoList.add(emailInfo);
                } catch (Exception e) {
                    logger.error("Error fetching email info {}: {}", message.getId(), e.getMessage());
                }
            }
        }

        GmailInboxDto inboxDto = new GmailInboxDto();
        inboxDto.setTotalEmails(emailInfoList.size());
        inboxDto.setEmails(emailInfoList);
        inboxDto.setFetchedAt(LocalDateTime.now());

        return inboxDto;
    }

    // ----------------- Get unread message count -----------------
    public Map<String, Object> getUnreadMessageCount(UserPrincipal currentUser)
            throws IOException, GeneralSecurityException {

        logger.info("Fetching unread count for user: {}", currentUser.getId());
        Gmail service = getGmailService();

        ListMessagesResponse response = service.users().messages()
                .list("me")
                .setQ("is:unread")
                .execute();

        Map<String, Object> result = new HashMap<>();
        int unreadCount = (response.getMessages() != null) ? response.getMessages().size() : 0;

        result.put("unreadCount", unreadCount);
        result.put("checkedAt", LocalDateTime.now());
        result.put("userId", currentUser.getId());

        logger.info("Unread count for user {}: {}", currentUser.getId(), unreadCount);
        return result;
    }

    // ----------------- Recursive attachment processing -----------------
    private void processAttachmentParts(UserPrincipal currentUser, Gmail service, Message message,
                                        List<DocumentDto> syncedDocs, List<MessagePart> parts,
                                        String subject, String sender) throws IOException {

        for (MessagePart part : parts) {
            if (part.getParts() != null && !part.getParts().isEmpty()) {
                // Recursively process nested parts
                processAttachmentParts(currentUser, service, message, syncedDocs, part.getParts(), subject, sender);
            } else {
                processSingleAttachment(currentUser, service, message, syncedDocs, part, subject, sender);
            }
        }
    }

    // ----------------- Process single attachment -----------------
    private void processSingleAttachment(UserPrincipal currentUser, Gmail service, Message message,
                                         List<DocumentDto> syncedDocs, MessagePart part,
                                         String subject, String sender) throws IOException {

        if (part.getFilename() == null || part.getFilename().isEmpty()) {
            return;
        }

        String attachmentId = part.getBody().getAttachmentId();
        if (attachmentId == null) {
            return;
        }

        // Check if attachment type is supported
        String mimeType = part.getMimeType();
        if (!SUPPORTED_ATTACHMENT_TYPES.contains(mimeType)) {
            logger.warn("Skipping unsupported attachment type: {} for file: {}", mimeType, part.getFilename());
            return;
        }

        try {
            MessagePartBody attachment = service.users().messages()
                    .attachments()
                    .get("me", message.getId(), attachmentId)
                    .execute();

            byte[] fileData = Base64.getUrlDecoder().decode(attachment.getData());
            if (fileData.length == 0) {
                logger.warn("Skipping empty attachment: {}", part.getFilename());
                return;
            }

            // Create temporary file
            String sanitizedFilename = sanitizeFilename(part.getFilename());
            File tempFile = File.createTempFile("gmail_attach_", "_" + sanitizedFilename);

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

            syncedDocs.add(doc);

            // Log audit
            userRepository.findById(currentUser.getId()).ifPresent(user ->
                    logEmailAudit(doc, subject, sender, "[Attachment] " + sanitizedFilename, user)
            );

            // Clean up temp file
            if (!tempFile.delete()) {
                logger.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
            }

            logger.info("Successfully processed attachment: {} for email: {}", sanitizedFilename, subject);

        } catch (Exception e) {
            logger.error("Error processing attachment {}: {}", part.getFilename(), e.getMessage());
        }
    }

    // ----------------- Helper: Create EmailInfoDto -----------------
    private EmailInfoDto createEmailInfoDto(Message message) {
        EmailInfoDto emailInfo = new EmailInfoDto();
        emailInfo.setMessageId(message.getId());
        emailInfo.setSubject(getEmailSubject(message));
        emailInfo.setSender(getEmailSender(message));
        emailInfo.setSnippet(message.getSnippet());

        // Convert timestamp
        if (message.getInternalDate() != null) {
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(message.getInternalDate()),
                    ZoneId.systemDefault()
            );
            emailInfo.setReceivedAt(dateTime);
        }

        // Check for attachments
        boolean hasAttachments = false;
        if (message.getPayload() != null && message.getPayload().getParts() != null) {
            hasAttachments = message.getPayload().getParts().stream()
                    .anyMatch(part -> part.getFilename() != null && !part.getFilename().isEmpty());
        }
        emailInfo.setHasAttachments(hasAttachments);

        return emailInfo;
    }

    // ----------------- Audit logging -----------------
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
            logger.error("Failed to log audit for user {}: {}", user.getId(), e.getMessage());
        }
    }

    // ----------------- Gmail Authentication -----------------
    private Gmail getGmailService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = new FileInputStream(credentialsFilePath);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    // ----------------- Process email as Document -----------------
    private DocumentDto processEmailAsDocument(Message message, UserPrincipal currentUser) {
        try {
            String emailContent = getEmailContent(message);
            String subject = getEmailSubject(message);

            if (emailContent.trim().isEmpty()) {
                logger.warn("Skipping empty email content for subject: {}", subject);
                return null;
            }

            // Create email content file
            String sanitizedSubject = sanitizeFilename(subject);
            File tempFile = File.createTempFile("email_", ".txt");

            try (FileWriter writer = new FileWriter(tempFile, StandardCharsets.UTF_8)) {
                // Add email metadata to content
                writer.write("Subject: " + subject + "\n");
                writer.write("From: " + getEmailSender(message) + "\n");
                writer.write("Date: " + getEmailDate(message) + "\n");
                writer.write("Message ID: " + message.getId() + "\n");
                writer.write("\n--- Email Content ---\n\n");
                writer.write(emailContent);
            }

            MultipartFile emailFile = new CustomMultipartFile(
                    tempFile,
                    sanitizedSubject + ".txt",
                    "text/plain"
            );

            DocumentDto doc = documentService.uploadDocument(
                    emailFile,
                    Document.DocumentSource.GMAIL,
                    null,
                    Set.of("email", "gmail", "text-content"),
                    currentUser
            );

            // Log audit
            userRepository.findById(currentUser.getId()).ifPresent(user ->
                    logEmailAudit(doc, subject, getEmailSender(message), "[Email Content] " + subject, user)
            );

            // Clean up temp file
            if (!tempFile.delete()) {
                logger.warn("Failed to delete temp file: {}", tempFile.getAbsolutePath());
            }

            return doc;

        } catch (Exception e) {
            logger.error("Error processing email as document: {}", e.getMessage());
            return null;
        }
    }

    // ----------------- Helper methods -----------------
    private String getEmailContent(Message message) {
        StringBuilder content = new StringBuilder();
        MessagePart payload = message.getPayload();

        if (payload == null) {
            return "";
        }

        if (payload.getBody() != null && payload.getBody().getData() != null) {
            try {
                byte[] data = Base64.getUrlDecoder().decode(payload.getBody().getData());
                content.append(new String(data, StandardCharsets.UTF_8));
            } catch (Exception e) {
                logger.error("Error decoding email body: {}", e.getMessage());
            }
        } else if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                if ("text/plain".equals(part.getMimeType()) &&
                        part.getBody() != null && part.getBody().getData() != null) {
                    try {
                        byte[] data = Base64.getUrlDecoder().decode(part.getBody().getData());
                        content.append(new String(data, StandardCharsets.UTF_8));
                        break;
                    } catch (Exception e) {
                        logger.error("Error decoding email part: {}", e.getMessage());
                    }
                }
            }
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
        // Remove or replace invalid filename characters
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}