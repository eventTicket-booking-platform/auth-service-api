package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.api.UserController;
import com.ec7205.event_hub.auth_service_api.config.*;
import com.ec7205.event_hub.auth_service_api.service.SystemUserService;
import com.ec7205.event_hub.auth_service_api.dto.request.SystemUserRequestDto;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class})
@ActiveProfiles("test")
class UserManagementAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean JwtService jwtService;
    @MockBean SystemUserService users;
    @MockBean com.ec7205.event_hub.auth_service_api.utils.PasswordGenerator passwordGenerator;

    @BeforeEach void clearStartupCalls() { clearInvocations(users); }

    @Test void customerAndHostRetainOwnProfileAccess() throws Exception {
        for (String role : List.of("CUSTOMER", "HOST")) {
            token(role);
            mvc.perform(get("/user-service/api/v1/users/get-user-details").header("Authorization", "Bearer " + role))
                    .andExpect(status().isOk());
        }
    }

    @Test void hostAndCustomerCannotEnumeratePlatformUsersOrResolveEmails() throws Exception {
        for (String role : List.of("HOST", "CUSTOMER")) {
            token(role);
            mvc.perform(get("/user-service/api/v1/users/all").header("Authorization", "Bearer " + role))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/user-service/api/v1/users/resolve-user-id?email=test@example.com").header("Authorization", "Bearer " + role))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(users);
    }
    @Test void adminRetainsUserManagementAccess() throws Exception {
        token("ADMIN");
        mvc.perform(get("/user-service/api/v1/users/all").header("Authorization", "Bearer ADMIN"))
                .andExpect(status().isOk());
        verify(users).getAllUsers(any());
    }
    private void token(String role) {
        when(decoder.decode(role)).thenReturn(Jwt.withTokenValue(role).header("alg", "none")
                .subject("subject").claim("realm_access", Map.of("roles", List.of(role))).build());
    }

    @Test void publicSignupCannotBindRoleOrAdminFlagsToTheRegistrationDto() throws Exception {
        mvc.perform(post("/user-service/api/v1/users/visitors/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"Applicant","lastName":"User","email":"applicant@example.test",
                         "password":"test-only-value","role":"host","roles":["admin"],"isAdmin":true,
                         "userId":"forged","emailVerified":true}
                        """))
                .andExpect(status().isCreated());
        ArgumentCaptor<SystemUserRequestDto> request = ArgumentCaptor.forClass(SystemUserRequestDto.class);
        verify(users).createUser(request.capture());
        assertEquals("applicant@example.test", request.getValue().getEmail());
        assertEquals(Set.of("firstName", "lastName", "email", "password", "contact"),
                Arrays.stream(SystemUserRequestDto.class.getDeclaredFields()).map(java.lang.reflect.Field::getName)
                        .filter(name -> !name.startsWith("$")).collect(java.util.stream.Collectors.toSet()));
        verify(users, never()).initializeHosts(any());
        verify(users, never()).initializeAdmins(any());
    }
}
