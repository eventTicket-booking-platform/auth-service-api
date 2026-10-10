package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.config.KeycloakSecurityUtil;
import com.ec7205.event_hub.auth_service_api.entity.*;
import com.ec7205.event_hub.auth_service_api.exceptions.*;
import com.ec7205.event_hub.auth_service_api.repo.HostApplicationRepo;
import com.ec7205.event_hub.auth_service_api.service.impl.AdminHostApplicationServiceImpl;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminHostApplicationServiceTest {
    private final HostApplicationRepo applications = mock(HostApplicationRepo.class);
    private final KeycloakSecurityUtil keycloakUtil = mock(KeycloakSecurityUtil.class);
    private final Keycloak keycloak = mock(Keycloak.class, RETURNS_DEEP_STUBS);
    private final AdminHostApplicationServiceImpl service = new AdminHostApplicationServiceImpl(applications, keycloakUtil);
    private HostApplication application;
    private RoleScopeResource roles;
    private RoleRepresentation host;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "realm", "test-realm");
        application = HostApplication.builder().id("application-id")
                .systemUser(SystemUser.builder().userId("local-id").keycloakId("keycloak-id")
                        .firstName("Applicant").lastName("User").email("applicant@example.test").build())
                .organizationName("Org").status(HostApplicationStatus.PENDING).appliedAt(Instant.now())
                .rejectionReason("Previous rejection").build();
        when(applications.findByIdForUpdate("application-id")).thenReturn(Optional.of(application));
        when(applications.saveAndFlush(application)).thenAnswer(invocation -> invocation.getArgument(0));
        when(keycloakUtil.getKeycloakInstance()).thenReturn(keycloak);
        when(keycloak.realm("test-realm").users().get("keycloak-id").toRepresentation())
                .thenReturn(new UserRepresentation());
        roles = keycloak.realm("test-realm").users().get("keycloak-id").roles().realmLevel();
        when(roles.listAll()).thenReturn(List.of());
        host = new RoleRepresentation();
        host.setName("host");
        when(keycloak.realm("test-realm").roles().get("host").toRepresentation()).thenReturn(host);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void listsAllApplicationsWithPaginationAndApplicantDetails() {
        Pageable pageable = PageRequest.of(1, 2);
        when(applications.findAll(pageable)).thenReturn(new PageImpl<>(List.of(application), pageable, 3));
        var result = service.list(null, pageable);
        assertEquals(3, result.dataCount());
        assertEquals("application-id", result.dataList().get(0).applicationId());
        assertEquals("local-id", result.dataList().get(0).userId());
        assertEquals("applicant@example.test", result.dataList().get(0).email());
        verify(applications).findAll(pageable);
    }

    @Test
    void filtersPendingApplicationsInRepositoryBeforePagination() {
        Pageable pageable = PageRequest.of(0, 20);
        when(applications.findByStatus(HostApplicationStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(application)));
        assertEquals(HostApplicationStatus.PENDING, service.list(HostApplicationStatus.PENDING, pageable)
                .dataList().get(0).status());
        verify(applications, never()).findAll(any(Pageable.class));
    }

    @Test
    void approvalGrantsOnlyHostToExistingUserAndRecordsAdminReview() {
        Instant before = Instant.now();
        var result = service.approve("application-id", "admin-subject");
        assertEquals(HostApplicationStatus.APPROVED, result.status());
        assertEquals("admin-subject", result.reviewedBy());
        assertFalse(result.reviewedAt().isBefore(before));
        assertNull(result.rejectionReason());
        verify(keycloak.realm("test-realm").users().get("keycloak-id")).toRepresentation();
        verify(roles).add(List.of(host));
        verify(roles, never()).remove(anyList());
        verify(keycloak.realm("test-realm").users(), never()).create(any());
        verify(keycloak.realm("test-realm").roles(), never()).get("admin");
        verify(applications).saveAndFlush(application);
        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(roles, never()).remove(anyList());
    }

    @Test
    void rejectionRecordsReasonAndReviewWithoutCallingKeycloak() {
        var result = service.reject("application-id", "admin-subject", "  Need more information  ");
        assertEquals(HostApplicationStatus.REJECTED, result.status());
        assertEquals("admin-subject", result.reviewedBy());
        assertNotNull(result.reviewedAt());
        assertEquals("Need more information", result.rejectionReason());
        verifyNoInteractions(keycloakUtil);
        verify(applications).saveAndFlush(application);
    }

    @ParameterizedTest
    @EnumSource(value = HostApplicationStatus.class, names = {"APPROVED", "REJECTED"})
    void terminalApplicationsCannotBeApprovedOrRejectedAgain(HostApplicationStatus status) {
        application.setStatus(status);
        assertThrows(DuplicateEntryException.class, () -> service.approve("application-id", "admin"));
        assertThrows(DuplicateEntryException.class, () -> service.reject("application-id", "admin", "Reason"));
        verifyNoInteractions(keycloakUtil);
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void unknownApplicationCannotBeReviewed() {
        assertThrows(EntryNotFoundException.class, () -> service.approve("unknown", "admin"));
        assertThrows(EntryNotFoundException.class, () -> service.reject("unknown", "admin", "Reason"));
        verifyNoInteractions(keycloakUtil);
    }

    @Test
    void missingKeycloakUserDoesNotApproveOrGrantRole() {
        when(keycloak.realm("test-realm").users().get("keycloak-id").toRepresentation())
                .thenThrow(new NotFoundException());
        assertThrows(EntryNotFoundException.class, () -> service.approve("application-id", "admin"));
        assertEquals(HostApplicationStatus.PENDING, application.getStatus());
        verify(roles, never()).add(anyList());
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void missingKeycloakIdCannotApprove() {
        application.getSystemUser().setKeycloakId(null);
        assertThrows(EntryNotFoundException.class, () -> service.approve("application-id", "admin"));
        verifyNoInteractions(keycloakUtil);
    }

    @Test
    void failedRoleGrantDoesNotPersistApproval() {
        doThrow(new IllegalStateException("Keycloak unavailable")).when(roles).add(anyList());
        assertThrows(IllegalStateException.class, () -> service.approve("application-id", "admin"));
        assertEquals(HostApplicationStatus.PENDING, application.getStatus());
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void rollbackRemovesOnlyNewlyGrantedRole() {
        service.approve("application-id", "admin");
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(roles).remove(List.of(host));
    }

    @Test
    void preExistingHostRoleIsNeitherAddedAgainNorRemovedOnRollback() {
        when(roles.listAll()).thenReturn(List.of(host));
        assertEquals(HostApplicationStatus.APPROVED, service.approve("application-id", "admin").status());
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(roles, never()).add(anyList());
        verify(roles, never()).remove(anyList());
    }

    @Test
    void unknownTransactionOutcomeDoesNotRemovePotentiallyCommittedRole() {
        service.approve("application-id", "admin");
        complete(TransactionSynchronization.STATUS_UNKNOWN);
        verify(roles, never()).remove(anyList());
    }

    @Test
    void compensationFailureDoesNotHideOriginalDatabaseFailure() {
        var failure = new IllegalStateException("DB unavailable");
        when(applications.saveAndFlush(application)).thenThrow(failure);
        doThrow(new IllegalStateException("Keycloak unavailable")).when(roles).remove(anyList());
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.approve("application-id", "admin")));
        assertDoesNotThrow(() -> complete(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(roles).remove(List.of(host));
    }

    @Test
    void invalidAdminIdentityAndRejectionReasonAreRejectedBeforeMutation() {
        assertThrows(UnauthorizedException.class, () -> service.approve("application-id", " "));
        for (String reason : Arrays.asList(null, " ", "x".repeat(2001))) {
            assertThrows(BadRequestException.class, () -> service.reject("application-id", "admin", reason));
        }
        verify(applications, never()).saveAndFlush(any());
        verifyNoInteractions(keycloakUtil);
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }
}
