package com.example.KochiRailMetroProject.KochiRailMetro.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "gmail_sync_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GmailSyncHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gmail_message_id", unique = true, nullable = false)
    private String gmailMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "email_subject")
    private String emailSubject;

    @Column(name = "email_sender")
    private String emailSender;

    @Column(name = "has_attachments")
    private Boolean hasAttachments = false;

    @Column(name = "attachments_count")
    private Integer attachmentsCount = 0;

    @CreationTimestamp
    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}