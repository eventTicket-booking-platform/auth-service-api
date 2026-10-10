package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.config.KeycloakSecurityUtil;
import com.ec7205.event_hub.auth_service_api.entity.*;
import com.ec7205.event_hub.auth_service_api.exceptions.DuplicateEntryException;
import com.ec7205.event_hub.auth_service_api.repo.*;
import com.ec7205.event_hub.auth_service_api.service.AdminHostApplicationService;
import com.ec7205.event_hub.auth_service_api.service.impl.AdminHostApplicationServiceImpl;
import com.ec7205.event_hub.auth_service_api.service.impl.HostApplicationServiceImpl;
import com.ec7205.event_hub.auth_service_api.service.HostApplicationService;
import com.ec7205.event_hub.auth_service_api.dto.request.HostApplicationRequestDto;
import com.ec7205.event_hub.auth_service_api.exceptions.EntryNotFoundException;
import com.ec7205.event_hub.auth_service_api.messaging.AuthNotificationEventPublisher;
import org.junit.jupiter.api.*;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.representations.idm.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-host-applications;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=SYSTEM_USER",
        "spring.jpa.show-sql=false", "keycloak.config.realm=test-realm"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AdminHostApplicationServiceImpl.class, HostApplicationServiceImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminHostApplicationPersistenceTest {
    @Autowired SystemUserRepo users;
    @Autowired HostApplicationRepo applications;
    @Autowired AdminHostApplicationService service;
    @Autowired HostApplicationService applicantService;
    @Autowired PlatformTransactionManager transactions;
    @MockBean KeycloakSecurityUtil keycloakUtil;
    @MockBean AuthNotificationEventPublisher publisher;
    private RoleScopeResource roles;
    private RoleRepresentation host;
    private String applicationId;

    @BeforeEach
    void setup() {
        applications.deleteAll();
        users.deleteAll();
        var user = users.saveAndFlush(SystemUser.builder().userId("local-id").keycloakId("keycloak-id")
                .firstName("Applicant").lastName("User").email("applicant@example.test")
                .isActive(true).isEnabled(true).isEmailVerified(true).build());
        applicationId = applications.saveAndFlush(HostApplication.builder().systemUser(user)
                .organizationName("Org").status(HostApplicationStatus.PENDING).appliedAt(Instant.now()).build()).getId();
        Keycloak keycloak = mock(Keycloak.class, RETURNS_DEEP_STUBS);
        when(keycloakUtil.getKeycloakInstance()).thenReturn(keycloak);
        when(keycloak.realm("test-realm").users().get("keycloak-id").toRepresentation())
                .thenReturn(new UserRepresentation());
        roles = keycloak.realm("test-realm").users().get("keycloak-id").roles().realmLevel();
        when(roles.listAll()).thenReturn(List.of());
        host = new RoleRepresentation();
        host.setName("host");
        when(keycloak.realm("test-realm").roles().get("host").toRepresentation()).thenReturn(host);
    }

    @Test
    void approvalCommitsReviewAndPreservesExistingUser() {
        service.approve(applicationId, "admin-subject");
        var saved = applications.findById(applicationId).orElseThrow();
        assertEquals(HostApplicationStatus.APPROVED, saved.getStatus());
        assertEquals("admin-subject", saved.getReviewedBy());
        assertNotNull(saved.getReviewedAt());
        assertNull(saved.getRejectionReason());
        assertEquals(1, users.count());
        verify(roles).add(List.of(host));
        verify(roles, never()).remove(anyList());
        verify(publisher).publish(argThat(event -> "HOST_APPLICATION_APPROVED".equals(event.getType())));
    }

    @Test
    void rollbackAfterFlushRestoresPendingAndCompensatesNewHostGrant() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            assertEquals(HostApplicationStatus.APPROVED, service.approve(applicationId, "admin").status());
            transaction.setRollbackOnly();
        });
        assertEquals(HostApplicationStatus.PENDING, applications.findById(applicationId).orElseThrow().getStatus());
        verify(roles).add(List.of(host));
        verify(roles).remove(List.of(host));
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectionCommitsWithoutKeycloakCalls() {
        service.reject(applicationId, "admin-subject", "More information required");
        var saved = applications.findById(applicationId).orElseThrow();
        assertEquals(HostApplicationStatus.REJECTED, saved.getStatus());
        assertEquals("More information required", saved.getRejectionReason());
        assertEquals("admin-subject", saved.getReviewedBy());
        verifyNoInteractions(keycloakUtil);
        verify(publisher).publish(argThat(event -> "HOST_APPLICATION_REJECTED".equals(event.getType())));
    }

    @Test
    void rejectionRollbackAfterFlushLeavesPendingAndDoesNotPublish() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            service.reject(applicationId, "admin", "Reason");
            verifyNoInteractions(publisher);
            transaction.setRollbackOnly();
        });
        assertEquals(HostApplicationStatus.PENDING, applications.findById(applicationId).orElseThrow().getStatus());
        verifyNoInteractions(publisher);
    }

    @Test
    void competingApproveAndRejectAllowExactlyOneReview() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> approve = executor.submit(() -> review(ready, start, true));
            Future<Boolean> reject = executor.submit(() -> review(ready, start, false));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            boolean approved = approve.get(10, TimeUnit.SECONDS);
            boolean rejected = reject.get(10, TimeUnit.SECONDS);
            assertNotEquals(approved, rejected);
            var saved = applications.findById(applicationId).orElseThrow();
            assertEquals(approved ? HostApplicationStatus.APPROVED : HostApplicationStatus.REJECTED, saved.getStatus());
            verify(roles, times(approved ? 1 : 0)).add(List.of(host));
            verify(publisher, times(1)).publish(any());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private boolean review(CountDownLatch ready, CountDownLatch start, boolean approve) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
        try {
            if (approve) service.approve(applicationId, "approving-admin");
            else service.reject(applicationId, "rejecting-admin", "Reason");
            return true;
        } catch (DuplicateEntryException conflict) {
            return false;
        }
    }

    @Test
    void applicantCanBeRejectedResubmitAndBeApprovedWithoutDuplicateUsersOrApplications() {
        applications.deleteById(applicationId);
        String email = "applicant@example.test";
        assertThrows(EntryNotFoundException.class, () -> applicantService.getCurrent(email));
        var request = new HostApplicationRequestDto("Org", "Community events");
        var submitted = applicantService.apply(email, request);
        assertEquals(HostApplicationStatus.PENDING, submitted.status());
        assertThrows(DuplicateEntryException.class, () -> applicantService.apply(email, request));
        service.reject(submitted.id(), "admin", "More information");
        assertEquals("More information", applicantService.getCurrent(email).rejectionReason());
        verifyNoInteractions(keycloakUtil);
        assertThrows(DuplicateEntryException.class, () -> service.approve(submitted.id(), "admin"));
        var resubmitted = applicantService.apply(email, request);
        assertEquals(submitted.id(), resubmitted.id());
        assertEquals(HostApplicationStatus.PENDING, resubmitted.status());
        assertNull(resubmitted.reviewedBy());
        assertNull(resubmitted.rejectionReason());
        service.approve(submitted.id(), "admin");
        assertEquals(HostApplicationStatus.APPROVED, applicantService.getCurrent(email).status());
        assertThrows(DuplicateEntryException.class, () -> applicantService.apply(email, request));
        assertThrows(DuplicateEntryException.class, () -> service.approve(submitted.id(), "admin"));
        assertThrows(DuplicateEntryException.class, () -> service.reject(submitted.id(), "admin", "Reason"));
        verify(roles).add(List.of(host));
        verify(publisher).publish(argThat(event -> "HOST_APPLICATION_REJECTED".equals(event.getType())));
        verify(publisher).publish(argThat(event -> "HOST_APPLICATION_APPROVED".equals(event.getType())));
        assertEquals(1, applications.count());
        assertEquals(1, users.count());
    }
}
