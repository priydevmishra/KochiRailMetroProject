package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.DocumentDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Notification;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentWorkflow;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DepartmentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.NotificationRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
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

    // -------------------------
    // EXISTING METHOD
    // -------------------------
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
            notification.setActionRequired(priority == Notification.Priority.CRITICAL || priority == Notification.Priority.HIGH);
            notification.setActionUrl("/documents/" + document.getId());

            notificationRepository.save(notification);

            // Send email if CRITICAL
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

    // -------------------------
    // NEW NOTIFICATION METHODS
    // -------------------------

    @Async
    public void sendMaintenanceAlert(Document document, boolean urgent) {
        String title = urgent ? "🚨 Urgent Maintenance Required" : "Maintenance Notification";
        String message = urgent ?
                "Critical maintenance issue detected for document: " + document.getFilename() :
                "New maintenance document uploaded: " + document.getFilename();

        sendGenericNotification(document, title, message, urgent ? Notification.Priority.CRITICAL : Notification.Priority.HIGH);
    }

    @Async
    public void sendSafetyBulletin(Document document) {
        String title = "⚠ Safety Bulletin";
        String message = "A safety-related document has been uploaded: " + document.getFilename();
        sendGenericNotification(document, title, message, Notification.Priority.HIGH);
    }

    @Async
    public void sendIncidentAlert(Document document) {
        String title = "🚨 Incident Alert";
        String message = "An incident document requires immediate attention: " + document.getFilename();
        sendGenericNotification(document, title, message, Notification.Priority.CRITICAL);
    }

    @Async
    public void sendHRNotification(Document document) {
        String title = "📢 HR Notification";
        String message = "A new HR document has been uploaded: " + document.getFilename();
        sendGenericNotification(document, title, message, Notification.Priority.MEDIUM);
    }

    @Async
    public void sendDeadlineReminder(DocumentWorkflow workflow) {
        String title = "⏰ Workflow Deadline Reminder";
        String message = "The workflow for document " + workflow.getDocument().getFilename() +
                " is due on " + workflow.getDeadline();

        sendGenericNotification(workflow.getDocument(), title, message, Notification.Priority.HIGH);
    }

    // -------------------------
    // HELPER METHOD
    // -------------------------
    private void sendGenericNotification(Document document, String title, String message, Notification.Priority priority) {
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            Notification notification = new Notification();
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setUser(user);
            notification.setPriority(priority);
            notification.setActionRequired(priority == Notification.Priority.CRITICAL || priority == Notification.Priority.HIGH);
            notification.setActionUrl("/documents/" + document.getId());
            notificationRepository.save(notification);
        }
    }

    // -------------------------
    // UPLOAD DOCUMENT
    // -------------------------
    public DocumentDto uploadDocument(MultipartFile file,
                                      Document.DocumentSource source,
                                      Long categoryId,
                                      Set<String> tags,
                                      UserPrincipal currentUser) throws IOException {
        Document document = new Document();
        // TODO: set fields from file, category, tags, and user
        document = documentRepository.save(document);

        kmrlProcessor.processKMRLDocument(document);

        documentProcessingService.processDocumentAsync(document.getId());

        return new DocumentDto(document);
    }
}
