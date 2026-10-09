package com.example.spring_boot_project_api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

        @Value("${firebase.project-id}")
        private String projectId;

        @Value("${firebase.client-email}")
        private String clientEmail;

        @Value("${firebase.private-key}")
        private String privateKey;

        @Value("${firebase.private-key-id}")
        private String privateKeyId;

        @Value("${firebase.client-id}")
        private String clientId;

        @PostConstruct
        public void initializeFirebase() throws IOException {

                // Prevent Firebase from being initialized more than once
                if (!FirebaseApp.getApps().isEmpty()) {
                        return;
                }

                // .env stores \n as text.
                // Convert it back to real line breaks.
                String formattedPrivateKey = privateKey.replace("\\n", "\n");

                String serviceAccountJson = """
                                {
                                  "type": "service_account",
                                  "project_id": "%s",
                                  "private_key_id": "%s",
                                  "private_key": "%s",
                                  "client_email": "%s",
                                  "client_id": "%s"
                                }
                                """.formatted(
                                projectId,
                                privateKeyId,
                                formattedPrivateKey,
                                clientEmail,
                                clientId);

                GoogleCredentials credentials = GoogleCredentials.fromStream(
                                new ByteArrayInputStream(
                                                serviceAccountJson.getBytes(
                                                                StandardCharsets.UTF_8)));

                FirebaseOptions options = FirebaseOptions.builder()
                                .setCredentials(credentials)
                                .setProjectId(projectId)
                                .build();

                FirebaseApp.initializeApp(options);

                System.out.println("Firebase initialized successfully.");
        }
}