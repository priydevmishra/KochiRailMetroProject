package com.example.KochiRailMetroProject.KochiRailMetro.Service;


import com.example.KochiRailMetroProject.KochiRailMetro.Entity.AuditLog;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Entity.User;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.AuditLogRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.DocumentRepository;
import com.example.KochiRailMetroProject.KochiRailMetro.Repository.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    public AuditService(AuditLogRepository auditLogRepository,
                        UserRepository userRepository,
                        DocumentRepository documentRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
    }

    @Async
    @Transactional
    public void logAction(Long userId, Long documentId, String action, String details) {
        try {
            AuditLog auditLog = new AuditLog();

            if (userId != null) {
                User user = userRepository.findById(userId).orElse(null);
                auditLog.setUser(user);
            }

            if (documentId != null) {
                Document document = documentRepository.findById(documentId).orElse(null);
                auditLog.setDocument(document);
            }

            auditLog.setAction(action);
            auditLog.setDetails(details);

            // Get request details if available
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setIpAddress(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Log error but don't fail the main operation
            System.err.println("Failed to log audit action: " + e.getMessage());
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader == null) {
            return request.getRemoteAddr();
        } else {
            return xForwardedForHeader.split(",")[0];
        }
    }
}
