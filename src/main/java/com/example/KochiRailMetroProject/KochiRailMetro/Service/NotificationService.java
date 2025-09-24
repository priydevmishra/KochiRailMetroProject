package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.NotificationDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.*;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.*;
import com.example.KochiRailMetroProject.KochiRailMetro.Security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               DepartmentRepository departmentRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    // ======================= DOCUMENT PROCESSOR METHODS =======================
    public void sendRegulatoryAlert(Document document, String title, String message, int priorityLevel) {
        sendDocumentNotification(document, title, message, "HIGH");
    }

    public void sendMaintenanceAlert(Document document, boolean isUrgent) {
        String title = isUrgent ? "⚠️ Urgent Maintenance Required" : "Maintenance Document Uploaded";
        String message = "Document: " + document.getFilename();
        sendDocumentNotification(document, title, message, isUrgent ? "HIGH" : "MEDIUM");
    }

    public void sendSafetyBulletin(Document document) {
        sendDocumentNotification(document, "🛡️ Safety Bulletin", "Safety document uploaded: " + document.getFilename(), "HIGH");
    }

    public void sendIncidentAlert(Document document) {
        sendDocumentNotification(document, "🚨 Incident Alert", "Incident document requires immediate attention: " + document.getFilename(), "HIGH");
    }

    public void sendHRNotification(Document document) {
        sendDocumentNotification(document, "📄 HR Document Uploaded", "HR document: " + document.getFilename(), "MEDIUM");
    }

    public void sendTaskNotification(DocumentWorkflow workflow) {
        Notification notification = new Notification();
        notification.setTitle("📄 New Task Assigned");
        notification.setMessage("You have been assigned a new document task: " + workflow.getDocument().getFilename());
        notification.setType("TASK");
        notification.setPriority("MEDIUM");
        notification.setUser(workflow.getAssignedTo());
        notification.setDepartment(workflow.getAssignedTo().getDepartment());
        notification.setIsRead(false);
        notification.setSender(workflow.getCreatedBy());
        notificationRepository.save(notification);
    }

    public void sendDeadlineReminder(DocumentWorkflow workflow) {
        Notification notification = new Notification();
        notification.setTitle("Workflow Deadline Reminder");
        notification.setMessage("The workflow for document '" +
                workflow.getDocument().getFilename() +
                "' is nearing its deadline (" + workflow.getDeadline() + ").");
        notification.setType("REMINDER");
        notification.setPriority("HIGH");
        notification.setUser(workflow.getAssignedTo());
        notification.setDepartment(workflow.getAssignedTo().getDepartment());
        notification.setIsRead(false);
        notification.setSender(null); // system generated
        notificationRepository.save(notification);
    }

    private void sendDocumentNotification(Document document, String title, String message, String priority) {
        User uploadedBy = document.getUploadedBy();
        if (uploadedBy != null && uploadedBy.getDepartment() != null) {
            Notification notification = new Notification();
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType("DOCUMENT_ALERT");
            notification.setPriority(priority);
            notification.setUser(uploadedBy);
            notification.setDepartment(uploadedBy.getDepartment());
            notification.setIsRead(false);
            notification.setSender(null); // system
            notificationRepository.save(notification);
        }
    }

    // ======================= HELPER METHODS =======================
    private boolean hasRole(UserPrincipal userPrincipal, String roleName) {
        return userPrincipal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + roleName));
    }

    private NotificationDto convertToDto(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setType(notification.getType());
        dto.setPriority(notification.getPriority());
        dto.setIsRead(notification.getIsRead());
        dto.setCreatedAt(notification.getCreatedAt());
        if (notification.getSender() != null) {
            dto.setSenderName(notification.getSender().getFullName());
            dto.setSenderEmail(notification.getSender().getEmail());
        }
        if (notification.getUser() != null) {
            dto.setRecipientName(notification.getUser().getFullName());
            dto.setRecipientEmail(notification.getUser().getEmail());
        }
        if (notification.getDepartment() != null) {
            dto.setDepartmentId(notification.getDepartment().getId());
            dto.setDepartmentName(notification.getDepartment().getName());
        }
        return dto;
    }
}
