package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Notification;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DepartmentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.NotificationRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.DocumentProcessingService;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.EmailService;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.KMRLDocumentProcessor;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class NotificationService {

    @Autowired
    private KMRLDocumentProcessor kmrlProcessor;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final EmailService emailService;
    private final DocumentRepository documentRepository;
    private final DocumentProcessingService documentProcessingService;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               DepartmentRepository departmentRepository,
                               EmailService emailService,
                               DocumentRepository documentRepository,
                               DocumentProcessingService documentProcessingService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.emailService = emailService;
        this.documentRepository = documentRepository;
        this.documentProcessingService = documentProcessingService;
    }

    @Async
    public void sendRegulatoryAlert(Document document, String title, String message, Integer level) {
        Notification.Priority priority = getPriorityFromLevel(level);
        List<User> allUsers = userRepository.findAll();

        for (User user : allUsers) {
            Notification notification = new Notification();
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setUser(user);
            notification.setPriority(priority);
            notification.setActionRequired(priority == Notification.Priority.CRITICAL ||
                    priority == Notification.Priority.HIGH);
            notification.setActionUrl("/documents/" + document.getId());

            notificationRepository.save(notification);

            // Send email if CRITICAL and user allows EMAIL
            if (priority == Notification.Priority.CRITICAL &&
                    user.getNotificationPreferences() != null &&
                    user.getNotificationPreferences().contains("EMAIL")) {
                emailService.sendCriticalAlert(user.getEmail(), title, message);
            }
        }
    }

    private Notification.Priority getPriorityFromLevel(Integer level) {
        return switch (level) {
            case 1 -> Notification.Priority.LOW;
            case 2 -> Notification.Priority.MEDIUM;
            case 3 -> Notification.Priority.HIGH;
            case 4 -> Notification.Priority.CRITICAL;
            default -> Notification.Priority.MEDIUM;
        };
    }

    public DocumentDto uploadDocument(MultipartFile file,
                                      Document.DocumentSource source,
                                      Long categoryId,
                                      Set<String> tags,
                                      UserPrincipal currentUser) throws IOException {

        // ... your existing document creation logic ...

        Document document = new Document();
        // set properties of document (name, category, tags, etc.)

        // Save the document
        document = documentRepository.save(document);

        // KMRL-specific processing
        kmrlProcessor.processKMRLDocument(document);

        // Trigger async document processing
        documentProcessingService.processDocumentAsync(document.getId());

        // Convert to DTO and return
        return new DocumentDto(document);
    }
}
