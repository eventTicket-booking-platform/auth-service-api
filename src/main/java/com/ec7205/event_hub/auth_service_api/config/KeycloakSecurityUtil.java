package com.ec7205.event_hub.auth_service_api.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class KeycloakSecurityUtil {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakSecurityUtil.class);

    Keycloak keycloak;

    @Value("${keycloak.config.server-url}")
    private String serverUrl;
    @Value("${keycloak.config.realm}")
    private String realm;
    @Value("${keycloak.config.client-id}")
    private String clientId;
    @Value("${keycloak.config.grant-type}")
    private String grantType;
    @Value("${keycloak.config.name}")
    private String username;
    @Value("${keycloak.config.password}")
    private String password;
    @Value("${keycloak.config.secret}")
    private String secret;

    public Keycloak getKeycloakInstance() {
        if(keycloak == null) {
            try {
                logger.info("Initializing Keycloak connection...");
                logger.info("Server URL: {}", serverUrl);
                logger.info("Realm: {}", realm);
                logger.info("Client ID: {}", clientId);
                logger.info("Grant Type: {}", grantType);
                logger.info("Username: {}", username);
                // Don't log password for security

                keycloak = KeycloakBuilder.builder()
                        .serverUrl(serverUrl)
                        .realm(realm)
                        .clientId(clientId)
                        .clientSecret(secret)
                        .grantType(grantType)
                        .username(username)
                        .password(password)
                        .build();

                // Test the connection immediately
                logger.info("Testing Keycloak connection...");
                String token = keycloak.tokenManager().getAccessTokenString();
                logger.info("Keycloak connection successful! Token received.");

            } catch (Exception e) {
                logger.error("Failed to initialize Keycloak: {}", e.getMessage(), e);
                throw new RuntimeException("Keycloak initialization failed", e);
            }
        }
        return keycloak;
    }
}