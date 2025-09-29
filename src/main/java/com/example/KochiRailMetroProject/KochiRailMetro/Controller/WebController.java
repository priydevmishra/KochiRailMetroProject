package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    /**
     * Serve the OAuth2 authorization page
     */
    @GetMapping("/oauth")
    public String oauthPage() {
        return "oauth.html";
    }

    /**
     * Redirect root to OAuth page for easy access
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/oauth";
    }
}