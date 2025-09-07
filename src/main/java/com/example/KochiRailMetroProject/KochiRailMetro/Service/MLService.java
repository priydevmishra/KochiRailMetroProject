package com.example.KochiRailMetroProject.KochiRailMetro.Service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Map;
import java.util.HashMap;

@Service
public class MLService {

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    @Value("${ml.service.api-key}")
    private String mlApiKey;

    private final RestTemplate restTemplate;

    public MLService() {
        this.restTemplate = new RestTemplate();
    }

    public String generateSummary(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(mlApiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            requestBody.put("max_length", 150);
            requestBody.put("min_length", 50);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    mlServiceUrl + "/generate-summary", request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                return (String) responseBody.get("summary");
            }

            return "Summary generation failed";
        } catch (Exception e) {
            return "Error generating summary: " + e.getMessage();
        }
    }

    public String classifyCategory(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(mlApiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    mlServiceUrl + "/classify-category", request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                return (String) responseBody.get("category");
            }

            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
