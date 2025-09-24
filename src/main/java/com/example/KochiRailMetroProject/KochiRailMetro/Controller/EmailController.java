package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import com.example.KochiRailMetroProject.KochiRailMetro.Entity.Document;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.EmailService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/email")  // isko pehle regulatory, critical aur deadline ke hisaab se banaaya tha but shovansh ne baad me iskaa use nhi kiyaa, but word file me iski saari api likhna, GPT pe iski controller aur service layer ko daalke use pooch ke likh lena.
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    // Regulatory alert email
    @PostMapping("/regulatory")
    public String sendRegulatoryAlert(@RequestParam String toEmail,
                                      @RequestParam String filename) {
        Document doc = new Document();
        doc.setOriginalFilename(filename);
        doc.setCreatedAt(LocalDateTime.now()); // ✅ auto set current time
        doc.setId(1L); // dummy or real ID

        emailService.sendRegulatoryAlert(toEmail, doc);
        return "Regulatory Alert Email Sent to " + toEmail;
    }

    //  Critical alert email
    @PostMapping("/critical")
    public String sendCriticalAlert(@RequestParam String toEmail,
                                    @RequestParam String title,
                                    @RequestParam String message) {
        emailService.sendCriticalAlert(toEmail, title, message);
        return "Critical Alert Email Sent to " + toEmail;
    }

    // Deadline reminder email
    @PostMapping("/deadline")
    public String sendDeadlineReminder(@RequestParam String toEmail,
                                       @RequestParam String taskName,
                                       @RequestParam String deadline) {
        emailService.sendDeadlineReminder(toEmail, taskName, deadline);
        return "Deadline Reminder Email Sent to " + toEmail;
    }
}
