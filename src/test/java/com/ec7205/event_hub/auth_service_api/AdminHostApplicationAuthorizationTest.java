package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.api.AdminHostApplicationController;
import com.ec7205.event_hub.auth_service_api.config.*;
import com.ec7205.event_hub.auth_service_api.dto.response.AdminHostApplicationResponseDto;
import com.ec7205.event_hub.auth_service_api.dto.response.pagination.HostApplicationPageResponseDto;
import com.ec7205.event_hub.auth_service_api.entity.HostApplicationStatus;
import com.ec7205.event_hub.auth_service_api.exceptions.*;
import com.ec7205.event_hub.auth_service_api.service.AdminHostApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminHostApplicationController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class})
@ActiveProfiles("test")
class AdminHostApplicationAuthorizationTest {
    private static final String PATH = "/user-service/api/v1/host-applications/admin";
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean AdminHostApplicationService applications;

    @Test
    void adminCanListPendingApplicationsWithPagination() throws Exception {
        token("ADMIN");
        when(applications.list(eq(HostApplicationStatus.PENDING), any()))
                .thenReturn(new HostApplicationPageResponseDto(List.of(response(HostApplicationStatus.PENDING)), 1));
        mvc.perform(get(PATH + "?status=PENDING&page=1&size=5").header("Authorization", "Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dataCount").value(1))
                .andExpect(jsonPath("$.data.dataList[0].applicationId").value("application-id"))
                .andExpect(jsonPath("$.data.dataList[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.dataList[0].keycloakId").doesNotExist());
        verify(applications).list(eq(HostApplicationStatus.PENDING), argThat(page ->
                page.getPageNumber() == 1 && page.getPageSize() == 5));
    }

    @Test
    void adminCanListWithoutStatusFilter() throws Exception {
        token("ADMIN");
        when(applications.list(isNull(), any())).thenReturn(new HostApplicationPageResponseDto(List.of(), 0));
        mvc.perform(get(PATH).header("Authorization", "Bearer token")).andExpect(status().isOk());
        verify(applications).list(isNull(), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOST", "CUSTOMER", "USER"})
    void nonAdminsCannotListApproveOrReject(String role) throws Exception {
        token(role);
        mvc.perform(get(PATH).header("Authorization", "Bearer token")).andExpect(status().isForbidden());
        mvc.perform(patch(PATH + "/application-id/approve").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(PATH + "/application-id/reject").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reason\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(applications);
    }

    @Test
    void unauthenticatedCallersCannotListApproveOrReject() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(patch(PATH + "/application-id/approve")).andExpect(status().isUnauthorized());
        mvc.perform(patch(PATH + "/application-id/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Reason\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(applications);
    }

    @Test
    void adminCanApproveAndReviewIdentityComesFromJwtSubject() throws Exception {
        token("ADMIN");
        when(applications.approve("application-id", "admin-subject")).thenReturn(response(HostApplicationStatus.APPROVED));
        mvc.perform(patch(PATH + "/application-id/approve?reviewedBy=forged")
                        .header("Authorization", "Bearer token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewedBy\":\"forged\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedBy").value("admin-subject"));
        verify(applications).approve("application-id", "admin-subject");
    }

    @Test
    void adminCanRejectWithReasonAndJwtIdentity() throws Exception {
        token("ADMIN");
        when(applications.reject("application-id", "admin-subject", "Reason"))
                .thenReturn(response(HostApplicationStatus.REJECTED));
        mvc.perform(patch(PATH + "/application-id/reject").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reason\",\"reviewedBy\":\"forged\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        verify(applications).reject("application-id", "admin-subject", "Reason");
    }

    @Test
    void unknownApplicationReturnsNotFound() throws Exception {
        token("ADMIN");
        when(applications.approve("unknown", "admin-subject")).thenThrow(new EntryNotFoundException("Not found"));
        when(applications.reject("unknown", "admin-subject", "Reason")).thenThrow(new EntryNotFoundException("Not found"));
        mvc.perform(patch(PATH + "/unknown/approve").header("Authorization", "Bearer token"))
                .andExpect(status().isNotFound());
        mvc.perform(patch(PATH + "/unknown/reject").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reason\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTransitionUsesExistingConflictResponse() throws Exception {
        token("ADMIN");
        when(applications.approve("application-id", "admin-subject"))
                .thenThrow(new DuplicateEntryException("Only pending applications can be reviewed"));
        mvc.perform(patch(PATH + "/application-id/approve").header("Authorization", "Bearer token"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void invalidStatusReturnsBadRequest() throws Exception {
        token("ADMIN");
        mvc.perform(get(PATH + "?status=INVALID").header("Authorization", "Bearer token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(applications);
    }

    private void token(String role) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "none")
                .subject("admin-subject").claim("email", "admin@example.test")
                .claim("realm_access", Map.of("roles", List.of(role))).build());
    }

    private AdminHostApplicationResponseDto response(HostApplicationStatus status) {
        return new AdminHostApplicationResponseDto("application-id", "user-id", "Applicant", "User",
                "applicant@example.test", null, "Org", "Events", status, Instant.now(),
                Instant.now(), "admin-subject", status == HostApplicationStatus.REJECTED ? "Reason" : null);
    }
}
