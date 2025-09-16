package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class EmailInfoDto {

    private String messageId;
    private String subject;
    private String sender;
    private String snippet;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime receivedAt;

    private boolean hasAttachments;
    private boolean isUnread;

    // Default constructor
    public EmailInfoDto() {
    }

    // Parameterized constructor
    public EmailInfoDto(String messageId, String subject, String sender, String snippet,
                        LocalDateTime receivedAt, boolean hasAttachments, boolean isUnread) {
        this.messageId = messageId;
        this.subject = subject;
        this.sender = sender;
        this.snippet = snippet;
        this.receivedAt = receivedAt;
        this.hasAttachments = hasAttachments;
        this.isUnread = isUnread;
    }

    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public boolean isHasAttachments() {
        return hasAttachments;
    }

    public void setHasAttachments(boolean hasAttachments) {
        this.hasAttachments = hasAttachments;
    }

    public boolean isUnread() {
        return isUnread;
    }

    public void setUnread(boolean isUnread) {
        this.isUnread = isUnread;
    }

    // Optional: for debugging
    @Override
    public String toString() {
        return "EmailInfoDto{" +
                "messageId='" + messageId + '\'' +
                ", subject='" + subject + '\'' +
                ", sender='" + sender + '\'' +
                ", snippet='" + snippet + '\'' +
                ", receivedAt=" + receivedAt +
                ", hasAttachments=" + hasAttachments +
                ", isUnread=" + isUnread +
                '}';
    }
}
