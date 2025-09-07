package com.example.KochiRailMetroProject.KochiRailMetro.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class CloudStorageConfig {
    @Value("${kmrl-rail-project-sih}")
    private String projectId;

    @Value("${src/main/resources/kmrl-rail-project-sih-6548f231730d.json}")
    private String credentialsLocation;

    @Bean
    public Storage storage() throws IOException {
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new FileInputStream(credentialsLocation));

        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(credentials)
                .build()
                .getService();
    }
}
