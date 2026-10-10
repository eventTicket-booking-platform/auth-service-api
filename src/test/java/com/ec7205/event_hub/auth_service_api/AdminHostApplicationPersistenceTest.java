package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.config.KeycloakSecurityUtil;
import com.ec7205.event_hub.auth_service_api.entity.*;
import com.ec7205.event_hub.auth_service_api.exceptions.DuplicateEntryException;
import com.ec7205.event_hub.auth_service_api.repo.*;
import com.ec7205.event_hub.auth_service_api.service.AdminHostApplicationService;
import com.ec7205.event_hub.auth_service_api.service.impl.AdminHostApplicationServiceImpl;
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
@Import(AdminHostApplicationServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminHostApplicationPersistenceTest {
    @Autowired SystemUserRepo users;
    @Autowired HostApplicationRepo applications;
    @Autowired AdminHostApplicationService service;
    @Autowired PlatformTransactionManager transactions;
    @MockBean KeycloakSecurityUtil keycloakUtil;
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
    }

    @Test
    void rejectionCommitsWithoutKeycloakCalls() {
        service.reject(applicationId, "admin-subject", "More information required");
        var saved = applications.findById(applicationId).orElseThrow();
        assertEquals(HostApplicationStatus.REJECTED, saved.getStatus());
        assertEquals("More information required", saved.getRejectionReason());
        assertEquals("admin-subject", saved.getReviewedBy());
        verifyNoInteractions(keycloakUtil);
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
}
