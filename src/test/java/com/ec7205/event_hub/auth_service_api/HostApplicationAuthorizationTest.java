package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.api.HostApplicationController;
import com.ec7205.event_hub.auth_service_api.config.JwtAuthConverter;
import com.ec7205.event_hub.auth_service_api.config.SecurityConfig;
import com.ec7205.event_hub.auth_service_api.dto.request.HostApplicationRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.response.HostApplicationResponseDto;
import com.ec7205.event_hub.auth_service_api.entity.HostApplicationStatus;
import com.ec7205.event_hub.auth_service_api.exceptions.DuplicateEntryException;
import com.ec7205.event_hub.auth_service_api.service.HostApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HostApplicationController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class})
@ActiveProfiles("test")
class HostApplicationAuthorizationTest {
    private static final String PATH = "/user-service/api/v1/host-applications";
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean HostApplicationService applications;

    @ParameterizedTest
    @ValueSource(strings = {"USER", "CUSTOMER"})
    void identityComesFromJwtAndClientCannotSelectAnotherUserOrStatus(String role) throws Exception {
        token(role);
        when(applications.apply(eq("caller@example.test"), any())).thenReturn(response());
        mvc.perform(post(PATH).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"Org","reason":"Events","email":"victim@example.test",
                                 "userId":"victim-id","role":"host","status":"APPROVED"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"));
        ArgumentCaptor<HostApplicationRequestDto> request = ArgumentCaptor.forClass(HostApplicationRequestDto.class);
        verify(applications).apply(eq("caller@example.test"), request.capture());
        assertEquals("Org", request.getValue().getOrganizationName());
        assertEquals("Events", request.getValue().getReason());
    }

    @Test
    void unauthenticatedRequestsCannotSubmitOrRead() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"organizationName\":\"Org\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/me")).andExpect(status().isUnauthorized());
        verifyNoInteractions(applications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOST", "ADMIN", "unrelated"})
    void privilegedOrNonCustomerRolesAreForbiddenEvenWithUserRole(String role) throws Exception {
        if (role.equals("unrelated")) token(role);
        else token("USER", role);
        mvc.perform(post(PATH).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"Org\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH + "/me").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(applications);
    }

    @Test
    void readsOnlyJwtUsersApplicationIgnoringIdentityQueryParameters() throws Exception {
        token("USER");
        when(applications.getCurrent("caller@example.test")).thenReturn(response());
        mvc.perform(get(PATH + "/me?email=victim@example.test&userId=victim")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("application-id"));
        verify(applications).getCurrent("caller@example.test");
        verifyNoMoreInteractions(applications);
    }

    @Test
    void duplicateApplicationUsesExistingConflictResponseConvention() throws Exception {
        token("USER");
        when(applications.apply(anyString(), any())).thenThrow(new DuplicateEntryException("Already applied"));
        mvc.perform(post(PATH).header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"Org\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
    }

    private void token(String... roles) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "none")
                .subject("caller-id").claim("email", "caller@example.test")
                .claim("realm_access", Map.of("roles", List.of(roles))).build());
    }

    private HostApplicationResponseDto response() {
        Instant now = Instant.now();
        return new HostApplicationResponseDto("application-id", "Org", "Events", HostApplicationStatus.PENDING,
                now, null, null, null, now, now);
    }
}
