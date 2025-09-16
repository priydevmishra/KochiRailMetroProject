package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@kmrl.com}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8090}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendRegulatoryAlert(String toEmail, Document document) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("REGULATORY ALERT - New Document Received");
        message.setText("A new regulatory document has been received and requires your attention.\n\n" +
                "Document: " + document.getOriginalFilename() + "\n" +
                "Uploaded: " + document.getCreatedAt() + "\n\n" +
                "Please review at: " + baseUrl + "/documents/" + document.getId() + "\n\n" +
                "This is an automated message from KMRL Document Management System.");

        try {
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send regulatory alert email: " + e.getMessage());
        }
    }

    @Async
    public void sendCriticalAlert(String toEmail, String title, String messageText) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("CRITICAL ALERT - " + title);
        message.setText("CRITICAL ALERT\n\n" +
                messageText + "\n\n" +
                "Please take immediate action.\n" +
                "Access the system at: " + baseUrl + "\n\n" +
                "This is an automated message from KMRL Document Management System.");

        try {
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send critical alert email: " + e.getMessage());
        }
    }

    @Async
    public void sendDeadlineReminder(String toEmail, String taskName, String deadline) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("DEADLINE REMINDER - " + taskName);
        message.setText("This is a reminder that you have an upcoming deadline.\n\n" +
                "Task: " + taskName + "\n" +
                "Deadline: " + deadline + "\n\n" +
                "Please complete your task before the deadline.\n" +
                "Access the system at: " + baseUrl + "\n\n" +
                "This is an automated message from KMRL Document Management System.");

        try {
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send deadline reminder email: " + e.getMessage());
        }
    }
}
