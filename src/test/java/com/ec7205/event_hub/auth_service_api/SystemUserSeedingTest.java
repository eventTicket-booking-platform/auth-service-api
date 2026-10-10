package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.config.KeycloakSecurityUtil;
import com.ec7205.event_hub.auth_service_api.dto.request.SystemUserRequestDto;
import com.ec7205.event_hub.auth_service_api.entity.SystemUser;
import com.ec7205.event_hub.auth_service_api.messaging.AuthNotificationEventPublisher;
import com.ec7205.event_hub.auth_service_api.messaging.dto.AuthNotificationEvent;
import com.ec7205.event_hub.auth_service_api.repo.OtpRepo;
import com.ec7205.event_hub.auth_service_api.repo.SystemUserRepo;
import com.ec7205.event_hub.auth_service_api.service.impl.SystemUserServiceImpl;
import com.ec7205.event_hub.auth_service_api.utils.FileDataExtractor;
import com.ec7205.event_hub.auth_service_api.utils.OtpGenerator;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemUserSeedingTest {
    private static final String REALM = "test-realm";
    private static final String EMAIL = "seed@example.test";
    private final SystemUserRequestDto dto = new SystemUserRequestDto(
            "Seed", "User", EMAIL, "test-password", "0700000000");

    @Mock SystemUserRepo users;
    @Mock KeycloakSecurityUtil keycloakUtil;
    @Mock OtpRepo otps;
    @Mock OtpGenerator otpGenerator;
    @Mock AuthNotificationEventPublisher notifications;
    @Mock FileDataExtractor fileDataExtractor;
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) Keycloak keycloak;
    @Mock Response response;
    @InjectMocks SystemUserServiceImpl service;

    @BeforeEach
    void configureRealm() {
        ReflectionTestUtils.setField(service, "realm", REALM);
    }

    @ParameterizedTest
    @CsvSource({"host,true", "host,false", "admin,true"})
    void assignsRequestedRoleAndEnablesUsersWithoutSendingAdminPasswords(String role, boolean notify) throws Exception {
        ReflectionTestUtils.setField(service, "publishSeedHostNotifications", notify);
        RoleRepresentation realmRole = prepareSuccessfulCreation(role);

        initialize(role);

        ArgumentCaptor<UserRepresentation> representation = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(keycloak.realm(REALM).users()).create(representation.capture());
        assertTrue(representation.getValue().isEnabled());
        assertTrue(representation.getValue().isEmailVerified());
        assertEquals(EMAIL, representation.getValue().getUsername());
        assertEquals(dto.getPassword(), representation.getValue().getCredentials().get(0).getValue());
        verify(keycloak.realm(REALM).users().get("new-user").roles().realmLevel()).add(List.of(realmRole));

        ArgumentCaptor<SystemUser> saved = ArgumentCaptor.forClass(SystemUser.class);
        verify(users).save(saved.capture());
        assertEquals("new-user", saved.getValue().getUserId());
        assertEquals("new-user", saved.getValue().getKeycloakId());
        assertEquals(EMAIL, saved.getValue().getEmail());
        assertTrue(saved.getValue().isActive());
        assertTrue(saved.getValue().isEnabled());
        assertTrue(saved.getValue().isEmailVerified());
        assertTrue(saved.getValue().isAccountNonExpired());
        assertTrue(saved.getValue().isAccountNonLocked());
        assertTrue(saved.getValue().isCredentialsNonExpired());

        if ("host".equals(role) && notify) {
            ArgumentCaptor<AuthNotificationEvent> event = ArgumentCaptor.forClass(AuthNotificationEvent.class);
            verify(notifications).publish(event.capture());
            assertEquals("HOST_PASSWORD", event.getValue().getType());
            assertEquals(dto.getEmail(), event.getValue().getPayload().get("email"));
            assertEquals(dto.getPassword(), event.getValue().getPayload().get("password"));
        } else {
            verifyNoInteractions(notifications);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"host", "admin"})
    void existingLocalUserIsSkipped(String role) throws Exception {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(SystemUser.builder().userId("existing").build()));
        initialize(role);
        verify(users, never()).save(any());
        verifyNoInteractions(keycloakUtil, keycloak, otps, notifications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"host", "admin"})
    void removesKeycloakOnlyUserBeforeReseeding(String role) throws Exception {
        prepareSuccessfulCreation(role);
        UserRepresentation stale = new UserRepresentation();
        stale.setId("stale-user");
        when(keycloak.realm(REALM).users().search(EMAIL)).thenReturn(List.of(stale));

        initialize(role);

        var order = inOrder(keycloak.realm(REALM).users());
        order.verify(keycloak.realm(REALM).users()).delete("stale-user");
        order.verify(keycloak.realm(REALM).users()).create(any(UserRepresentation.class));
        verify(users).save(any(SystemUser.class));
    }

    @Test
    void failedKeycloakCreationDoesNotSaveOrNotify() throws Exception {
        when(keycloakUtil.getKeycloakInstance()).thenReturn(keycloak);
        when(keycloak.realm(REALM).users().search(EMAIL)).thenReturn(List.of());
        when(keycloak.realm(REALM).users().create(any())).thenReturn(response);
        when(response.getStatus()).thenReturn(409);
        service.initializeAdmins(List.of(dto));
        verify(users, never()).save(any());
        verifyNoInteractions(notifications);
    }

    private RoleRepresentation prepareSuccessfulCreation(String role) {
        when(keycloakUtil.getKeycloakInstance()).thenReturn(keycloak);
        when(keycloak.realm(REALM).users().search(EMAIL)).thenReturn(List.of());
        when(keycloak.realm(REALM).users().create(any())).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(URI.create("http://keycloak.test/users/new-user"));
        RoleRepresentation realmRole = new RoleRepresentation();
        realmRole.setName(role);
        when(keycloak.realm(REALM).roles().get(role).toRepresentation()).thenReturn(realmRole);
        UserRepresentation created = new UserRepresentation();
        created.setId("new-user");
        when(keycloak.realm(REALM).users().get("new-user").toRepresentation()).thenReturn(created);
        when(users.save(any(SystemUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return realmRole;
    }

    @Test
    void publicRegistrationAlwaysAssignsOnlyUserAndRequiresEmailVerification() throws Exception {
        when(keycloakUtil.getKeycloakInstance()).thenReturn(keycloak);
        when(keycloak.realm(REALM).users().search(EMAIL)).thenReturn(List.of());
        when(keycloak.realm(REALM).users().create(any())).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(URI.create("http://keycloak.test/users/new-user"));
        RoleRepresentation userRole = new RoleRepresentation();
        userRole.setName("user");
        when(keycloak.realm(REALM).roles().get("user").toRepresentation()).thenReturn(userRole);
        when(users.save(any(SystemUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(otpGenerator.generateOtp(5)).thenReturn("12345");
        service.createUser(dto);
        verify(keycloak.realm(REALM).users().get("new-user").roles().realmLevel()).add(List.of(userRole));
        verify(keycloak.realm(REALM).roles(), never()).get("host");
        verify(keycloak.realm(REALM).roles(), never()).get("admin");
        ArgumentCaptor<UserRepresentation> representation = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(keycloak.realm(REALM).users()).create(representation.capture());
        assertFalse(representation.getValue().isEnabled());
        assertFalse(representation.getValue().isEmailVerified());
        verify(notifications).publish(argThat(event -> "EMAIL_VERIFICATION_OTP".equals(event.getType())));
    }

    private void initialize(String role) throws Exception {
        if ("host".equals(role)) service.initializeHosts(List.of(dto));
        else service.initializeAdmins(List.of(dto));
    }
}
