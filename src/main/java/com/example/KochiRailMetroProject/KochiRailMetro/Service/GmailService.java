package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.util.CustomMultipartFile;
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
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

@Service
public class GmailService {

    private static final String APPLICATION_NAME = "KMRL Document Management";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);

    @Value("${google.gmail.credentials.file}")
    private String credentialsFilePath;

    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final CloudStorageService cloudStorageService;

    public GmailService(DocumentService documentService,
                        UserRepository userRepository,
                        CloudStorageService cloudStorageService) {
        this.documentService = documentService;
        this.userRepository = userRepository;
        this.cloudStorageService = cloudStorageService;
    }

    public List<DocumentDto> syncRecentEmails(UserPrincipal currentUser, int maxResults)
            throws IOException, GeneralSecurityException {

        Gmail service = getGmailService();
        List<DocumentDto> processedDocuments = new ArrayList<>();

        // List recent messages
        ListMessagesResponse response = service.users().messages()
                .list("me")
                .setMaxResults((long) maxResults)
                .setQ("has:attachment")
                .execute();

        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            return processedDocuments;
        }

        for (Message message : messages) {
            try {
                Message fullMessage = service.users().messages()
                        .get("me", message.getId())
                        .setFormat("full")
                        .execute();

                DocumentDto document = processEmailAsDocument(fullMessage, currentUser);
                if (document != null) {
                    processedDocuments.add(document);
                }
            } catch (Exception e) {
                // Log error and continue with next message
                System.err.println("Error processing email: " + e.getMessage());
            }
        }

        return processedDocuments;
    }

    public List<DocumentDto> syncEmailAttachments(UserPrincipal currentUser, int maxResults)
            throws IOException, GeneralSecurityException {

        Gmail service = getGmailService();
        List<DocumentDto> processedDocuments = new ArrayList<>();

        // List recent messages with attachments
        ListMessagesResponse response = service.users().messages()
                .list("me")
                .setMaxResults((long) maxResults)
                .setQ("has:attachment")
                .execute();

        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            return processedDocuments;
        }

        for (Message message : messages) {
            try {
                Message fullMessage = service.users().messages()
                        .get("me", message.getId())
                        .setFormat("full")
                        .execute();

                List<DocumentDto> attachmentDocs = processEmailAttachments(service, fullMessage, currentUser);
                processedDocuments.addAll(attachmentDocs);
            } catch (Exception e) {
                System.err.println("Error processing email attachments: " + e.getMessage());
            }
        }

        return processedDocuments;
    }

    private Gmail getGmailService() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        InputStream in = new FileInputStream(credentialsFilePath);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private DocumentDto processEmailAsDocument(Message message, UserPrincipal currentUser)
            throws IOException {

        // Convert email to text format
        String emailContent = getEmailContent(message);
        String subject = getEmailSubject(message);
        String sender = getEmailSender(message);

        // Create a temporary file for the email content
        File tempFile = File.createTempFile("email_", ".txt");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(emailContent);
        }

        // Upload email content as document
        MultipartFile emailFile = new CustomMultipartFile(tempFile, subject + ".txt", "text/plain");

        try {
            DocumentDto document = documentService.uploadDocument(
                    emailFile,
                    Document.DocumentSource.GMAIL,
                    null,
                    Set.of("email", "gmail"),
                    currentUser
            );

            // Add email-specific metadata
            addEmailMetadata(document.getId(), message, sender, subject);

            return document;
        } finally {
            tempFile.delete();
        }
    }

    private List<DocumentDto> processEmailAttachments(Gmail service, Message message,
                                                      UserPrincipal currentUser) throws IOException {

        List<DocumentDto> documents = new ArrayList<>();
        MessagePart payload = message.getPayload();

        if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                if (part.getFilename() != null && !part.getFilename().isEmpty()) {
                    try {
                        DocumentDto document = processAttachment(service, message.getId(),
                                part, currentUser);
                        if (document != null) {
                            documents.add(document);
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing attachment: " + e.getMessage());
                    }
                }
            }
        }

        return documents;
    }

    private DocumentDto processAttachment(Gmail service, String messageId, MessagePart part,
                                          UserPrincipal currentUser) throws IOException {

        String attachmentId = part.getBody().getAttachmentId();
        MessagePartBody attachmentBody = service.users().messages().attachments()
                .get("me", messageId, attachmentId).execute();

        byte[] fileData = Base64.getDecoder().decode(attachmentBody.getData());

        // Create temporary file for attachment
        File tempFile = File.createTempFile("attachment_", "_" + part.getFilename());
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(fileData);
        }

        MultipartFile attachmentFile = new CustomMultipartFile(tempFile,
                part.getFilename(), part.getMimeType());

        try {
            return documentService.uploadDocument(
                    attachmentFile,
                    Document.DocumentSource.GMAIL,
                    null,
                    Set.of("gmail", "attachment"),
                    currentUser
            );
        } finally {
            tempFile.delete();
        }
    }

    private String getEmailContent(Message message) {
        StringBuilder content = new StringBuilder();
        MessagePart payload = message.getPayload();

        if (payload.getBody() != null && payload.getBody().getData() != null) {
            byte[] data = Base64.getDecoder().decode(payload.getBody().getData());
            content.append(new String(data));
        } else if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                if ("text/plain".equals(part.getMimeType()) &&
                        part.getBody() != null && part.getBody().getData() != null) {
                    byte[] data = Base64.getDecoder().decode(part.getBody().getData());
                    content.append(new String(data));
                    break;
                }
            }
        }

        return content.toString();
    }

    private String getEmailSubject(Message message) {
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                if ("Subject".equals(header.getName())) {
                    return header.getValue();
                }
            }
        }
        return "No Subject";
    }

    private String getEmailSender(Message message) {
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                if ("From".equals(header.getName())) {
                    return header.getValue();
                }
            }
        }
        return "Unknown Sender";
    }

    private void addEmailMetadata(Long documentId, Message message, String sender, String subject) {
        // This would typically be implemented to add metadata to the document
        // For now, we'll leave it as a placeholder
    }
}

