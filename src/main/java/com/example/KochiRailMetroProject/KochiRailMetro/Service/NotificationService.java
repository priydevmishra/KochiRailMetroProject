package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.NotificationDto;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.DocumentWorkflow;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Department;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Notification;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.NotificationRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DepartmentRepository;
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

    // ======================= EXISTING METHODS =======================

    public NotificationDto sendNotificationToDepartmentManager(NotificationDto notificationDto, UserPrincipal currentUser) {
        if (!hasRole(currentUser, "ADMIN")) {
            throw new RuntimeException("Only admins can send notifications to department managers");
        }
        Department department = departmentRepository.findById(notificationDto.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        User manager = userRepository.findByDepartment(department).stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("MANAGER")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No manager found for this department"));

        Notification notification = new Notification();
        notification.setTitle(notificationDto.getTitle());
        notification.setMessage(notificationDto.getMessage());
        notification.setType(notificationDto.getType());
        notification.setPriority(notificationDto.getPriority());
        notification.setSender(userRepository.findById(currentUser.getId()).orElse(null));
        notification.setUser(manager);
        notification.setDepartment(department);
        notification.setIsRead(false);

        Notification savedNotification = notificationRepository.save(notification);
        return convertToDto(savedNotification);
    }

    public NotificationDto sendNotificationToDepartmentEmployees(NotificationDto notificationDto, UserPrincipal currentUser) {
        if (!hasRole(currentUser, "MANAGER")) {
            throw new RuntimeException("Only managers can send notifications to employees");
        }
        User manager = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));
        Department department = manager.getDepartment();
        if (department == null) {
            throw new RuntimeException("Manager not assigned to any department");
        }

        List<User> employees = userRepository.findByDepartment(department).stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("EMPLOYEE")))
                .collect(Collectors.toList());

        if (employees.isEmpty()) {
            throw new RuntimeException("No employees found in the department");
        }

        for (User employee : employees) {
            Notification notification = new Notification();
            notification.setTitle(notificationDto.getTitle());
            notification.setMessage(notificationDto.getMessage());
            notification.setType(notificationDto.getType());
            notification.setPriority(notificationDto.getPriority());
            notification.setSender(manager);
            notification.setUser(employee);
            notification.setDepartment(department);
            notification.setIsRead(false);
            notificationRepository.save(notification);
        }

        NotificationDto response = new NotificationDto();
        response.setTitle(notificationDto.getTitle());
        response.setMessage("Notification sent to " + employees.size() + " employees");
        response.setType(notificationDto.getType());
        response.setPriority(notificationDto.getPriority());
        return response;
    }

    public List<NotificationDto> getNotificationsForUser(UserPrincipal currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        return notifications.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Long getUnreadNotificationsCount(UserPrincipal currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return notificationRepository.countUnreadByUser(user);
    }

    public NotificationDto markAsRead(Long notificationId, UserPrincipal currentUser) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        if (!notification.getUser().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Cannot mark other user's notification as read");
        }
        notification.setIsRead(true);
        Notification savedNotification = notificationRepository.save(notification);
        return convertToDto(savedNotification);
    }

    public void markAllAsRead(UserPrincipal currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Notification> unreadNotifications = notificationRepository.findByUserAndIsReadFalse(user);
        for (Notification notification : unreadNotifications) {
            notification.setIsRead(true);
        }
        notificationRepository.saveAll(unreadNotifications);
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

    public List<NotificationDto> getSentNotifications(UserPrincipal currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Notification> sentNotifications = notificationRepository.findBySenderOrderByCreatedAtDesc(user);
        return sentNotifications.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // ======================= NEW METHODS FOR DOCUMENT PROCESSOR =======================

    public void sendRegulatoryAlert(Document document, String title, String message, int priority) {
        sendSystemNotificationToAllManagers(title, message, "REGULATORY", mapPriority(priority));
    }

    public void sendMaintenanceAlert(Document document, boolean urgent) {
        String title = urgent ? "🚨 Urgent Maintenance Document" : "🛠 Maintenance Document";
        String message = "A maintenance document has been uploaded: " + document.getFilename();
        String priority = urgent ? "CRITICAL" : "MEDIUM";
        sendSystemNotificationToEngineering(title, message, "MAINTENANCE", priority);
    }

    public void sendSafetyBulletin(Document document) {
        String title = "⚠ Safety Bulletin";
        String message = "A new safety document has been uploaded: " + document.getFilename();
        sendSystemNotificationToAllManagers(title, message, "SAFETY", "HIGH");
    }

    public void sendIncidentAlert(Document document) {
        String title = "🚨 Incident Alert";
        String message = "An incident document has been uploaded: " + document.getFilename();
        sendSystemNotificationToAllManagers(title, message, "INCIDENT", "CRITICAL");
    }

    public void sendHRNotification(Document document) {
        String title = "👥 HR Document";
        String message = "A new HR-related document has been uploaded: " + document.getFilename();
        sendSystemNotificationToHR(title, message, "HR", "MEDIUM");
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

    private void sendSystemNotificationToAllManagers(String title, String message, String type, String priority) {
        List<User> managers = userRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName().equals("MANAGER")))
                .collect(Collectors.toList());

        for (User manager : managers) {
            Notification notification = new Notification();
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(type);
            notification.setPriority(priority);
            notification.setUser(manager);
            notification.setDepartment(manager.getDepartment());
            notification.setIsRead(false);
            notification.setSender(null); // system generated
            notificationRepository.save(notification);
        }
    }

    private void sendSystemNotificationToEngineering(String title, String message, String type, String priority) {
        Department engineering = departmentRepository.findByCode("ENGINEERING").orElse(null);
        if (engineering == null) return;

        List<User> users = userRepository.findByDepartment(engineering);
        for (User user : users) {
            Notification notification = new Notification();
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(type);
            notification.setPriority(priority);
            notification.setUser(user);
            notification.setDepartment(engineering);
            notification.setIsRead(false);
            notification.setSender(null);
            notificationRepository.save(notification);
        }
    }

    private void sendSystemNotificationToHR(String title, String message, String type, String priority) {
        Department hr = departmentRepository.findByCode("HR").orElse(null);
        if (hr == null) return;

        List<User> users = userRepository.findByDepartment(hr);
        for (User user : users) {
            Notification notification = new Notification();
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(type);
            notification.setPriority(priority);
            notification.setUser(user);
            notification.setDepartment(hr);
            notification.setIsRead(false);
            notification.setSender(null);
            notificationRepository.save(notification);
        }
    }

    private String mapPriority(int priority) {
        switch (priority) {
            case 4: return "CRITICAL";
            case 3: return "HIGH";
            case 2: return "MEDIUM";
            default: return "LOW";
        }
    }
}
