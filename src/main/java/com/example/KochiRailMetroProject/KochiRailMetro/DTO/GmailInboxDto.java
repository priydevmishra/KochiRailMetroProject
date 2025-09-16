package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

public class GmailInboxDto {

    private int totalEmails;
    private List<EmailInfoDto> emails;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fetchedAt;

    // Default constructor
    public GmailInboxDto() {
    }

    // Parameterized constructor
    public GmailInboxDto(int totalEmails, List<EmailInfoDto> emails, LocalDateTime fetchedAt) {
        this.totalEmails = totalEmails;
        this.emails = emails;
        this.fetchedAt = fetchedAt;
    }

    // Getters and Setters
    public int getTotalEmails() {
        return totalEmails;
    }

    public void setTotalEmails(int totalEmails) {
        this.totalEmails = totalEmails;
    }

    public List<EmailInfoDto> getEmails() {
        return emails;
    }

    public void setEmails(List<EmailInfoDto> emails) {
        this.emails = emails;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    @Override
    public String toString() {
        return "GmailInboxDto{" +
                "totalEmails=" + totalEmails +
                ", emails=" + emails +
                ", fetchedAt=" + fetchedAt +
                '}';
    }
}