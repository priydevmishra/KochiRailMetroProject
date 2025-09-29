package com.example.KochiRailMetroProject.KochiRailMetro.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.GmailScopes;

@Service
public class OAuth2Service {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Service.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);
    private static final String USER_ID = "user";

    @Value("${google.gmail.credentials.file}")
    private String credentialsFilePath;

    @Value("${server.port}")
    private String serverPort;

    private GoogleAuthorizationCodeFlow flow;

    /**
     * Get the authorization URL for OAuth2 flow
     */
    public String getAuthorizationUrl() throws IOException, GeneralSecurityException {
        GoogleAuthorizationCodeFlow flow = getAuthorizationCodeFlow();
        return flow.newAuthorizationUrl()
                .setRedirectUri(getRedirectUri())
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();
    }

    /**
     * Exchange authorization code for access token
     */
    public boolean exchangeCodeForToken(String code) throws IOException, GeneralSecurityException {
        try {
            GoogleAuthorizationCodeFlow flow = getAuthorizationCodeFlow();
            
            GoogleTokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRedirectUri(getRedirectUri())
                    .execute();

            // Store the credential
            Credential credential = flow.createAndStoreCredential(tokenResponse, USER_ID);
            
            logger.info("OAuth2 credentials stored successfully for user: {}", USER_ID);
            return credential != null;
            
        } catch (Exception e) {
            logger.error("Failed to exchange authorization code for token: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if user is already authorized
     */
    public boolean isAuthorized() throws IOException, GeneralSecurityException {
        try {
            GoogleAuthorizationCodeFlow flow = getAuthorizationCodeFlow();
            Credential credential = flow.loadCredential(USER_ID);
            
            if (credential == null) {
                return false;
            }

            // Check if token needs refresh
            if (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60) {
                credential.refreshToken();
            }

            return true;
        } catch (Exception e) {
            logger.warn("Failed to check authorization status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Revoke OAuth2 authorization
     */
    public boolean revokeAuthorization() {
        try {
            GoogleAuthorizationCodeFlow flow = getAuthorizationCodeFlow();
            Credential credential = flow.loadCredential(USER_ID);
            
            if (credential != null) {
                // Delete stored credentials
                flow.getCredentialDataStore().delete(USER_ID);
                logger.info("OAuth2 authorization revoked successfully");
                return true;
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Failed to revoke OAuth2 authorization: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get credential for authenticated user
     */
    public Credential getCredential() throws IOException, GeneralSecurityException {
        GoogleAuthorizationCodeFlow flow = getAuthorizationCodeFlow();
        Credential credential = flow.loadCredential(USER_ID);
        
        if (credential != null && credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60) {
            credential.refreshToken();
        }
        
        return credential;
    }

    /**
     * Create and cache the authorization code flow
     */
    private GoogleAuthorizationCodeFlow getAuthorizationCodeFlow() throws IOException, GeneralSecurityException {
        if (flow == null) {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            
            try (InputStream in = new FileInputStream(credentialsFilePath)) {
                GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
                
                flow = new GoogleAuthorizationCodeFlow.Builder(
                        httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                        .setAccessType("offline")
                        .setApprovalPrompt("force")
                        .build();
            }
        }
        return flow;
    }

    /**
     * Get the redirect URI based on current configuration
     */
    private String getRedirectUri() {
        // Use localhost for development, and the production URL for deployment
        return "http://localhost:" + serverPort + "/api/v1/login/oauth2/code/google";
    }
}