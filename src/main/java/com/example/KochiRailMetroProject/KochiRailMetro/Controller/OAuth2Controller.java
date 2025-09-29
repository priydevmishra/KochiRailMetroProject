package com.example.KochiRailMetroProject.KochiRailMetro.Controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.example.KochiRailMetroProject.KochiRailMetro.DTO.ApiResponse;
import com.example.KochiRailMetroProject.KochiRailMetro.Service.OAuth2Service;

@RestController
@RequestMapping("/api/v1/login/oauth2")
public class OAuth2Controller {

    private final OAuth2Service oAuth2Service;

    public OAuth2Controller(OAuth2Service oAuth2Service) {
        this.oAuth2Service = oAuth2Service;
    }

    /**
     * Initiate OAuth2 flow - redirect user to Google authorization URL
     */
    @GetMapping("/authorize/google")
    public RedirectView initiateGoogleOAuth2() {
        try {
            String authorizationUrl = oAuth2Service.getAuthorizationUrl();
            return new RedirectView(authorizationUrl);
        } catch (Exception e) {
            // Redirect to error page with error message
            String errorMessage = URLEncoder.encode("OAuth2 initialization failed: " + e.getMessage(), StandardCharsets.UTF_8);
            return new RedirectView("/oauth/error?message=" + errorMessage);
        }
    }

    /**
     * Handle OAuth2 callback from Google
     */
@GetMapping("/code/google")
public ResponseEntity<ApiResponse<String>> handleGoogleCallback(
        @RequestParam String code,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String error) {
    
    try {
        if (error != null && !error.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "OAuth2 authorization failed: " + error));
        }

        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "No authorization code received"));
        }

        // Exchange authorization code for access token and store credentials
        boolean success = oAuth2Service.exchangeCodeForToken(code);
        
        if (success) {
            return ResponseEntity.ok(new ApiResponse<>(true, 
                "Google OAuth2 authorization successful. You can now sync Gmail emails.", 
                "OAuth2 credentials stored successfully"));
        } else {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to exchange authorization code for token"));
        }
        
    } catch (Exception e) {
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, "OAuth2 callback handling failed: " + e.getMessage()));
    }
}

    /**
     * Check OAuth2 authorization status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<String>> getOAuth2Status() {
        try {
            boolean isAuthorized = oAuth2Service.isAuthorized();
            if (isAuthorized) {
                return ResponseEntity.ok(new ApiResponse<>(true, 
                    "OAuth2 is authorized", "Google Gmail access is configured"));
            } else {
                return ResponseEntity.ok(new ApiResponse<>(false, 
                    "OAuth2 not authorized", "Please authorize Google Gmail access first"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Failed to check OAuth2 status: " + e.getMessage()));
        }
    }

    /**
     * Revoke OAuth2 authorization
     */
    @PostMapping("/revoke")
    public ResponseEntity<ApiResponse<String>> revokeOAuth2Authorization() {
        try {
            boolean success = oAuth2Service.revokeAuthorization();
            if (success) {
                return ResponseEntity.ok(new ApiResponse<>(true, 
                    "OAuth2 authorization revoked successfully", null));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(false, "Failed to revoke OAuth2 authorization"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, "Error revoking OAuth2 authorization: " + e.getMessage()));
        }
    }
}