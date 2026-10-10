package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.dto.request.HostApplicationRequestDto;
import com.ec7205.event_hub.auth_service_api.entity.*;
import com.ec7205.event_hub.auth_service_api.exceptions.DuplicateEntryException;
import com.ec7205.event_hub.auth_service_api.repo.HostApplicationRepo;
import com.ec7205.event_hub.auth_service_api.repo.SystemUserRepo;
import com.ec7205.event_hub.auth_service_api.service.HostApplicationService;
import com.ec7205.event_hub.auth_service_api.service.impl.HostApplicationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:host-applications;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=SYSTEM_USER",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(HostApplicationServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HostApplicationPersistenceTest {
    private static final String EMAIL = "customer@example.test";
    @Autowired SystemUserRepo users;
    @Autowired HostApplicationRepo applications;
    @Autowired HostApplicationService service;
    private SystemUser user;

    @BeforeEach
    void prepareUser() {
        applications.deleteAll();
        users.deleteAll();
        user = users.saveAndFlush(SystemUser.builder().userId("customer-id").keycloakId("customer-id")
                .firstName("Customer").lastName("User").email(EMAIL)
                .isActive(true).isEnabled(true).isEmailVerified(true).build());
    }

    @Test
    void persistsTimestampsAndEnforcesOneApplicationPerUserInDatabase() {
        var created = service.apply(EMAIL, new HostApplicationRequestDto("Org", null));
        assertNotNull(created.id());
        assertNotNull(created.createdAt());
        assertNotNull(created.updatedAt());
        assertEquals(HostApplicationStatus.PENDING, service.getCurrent(EMAIL).status());
        assertThrows(DataIntegrityViolationException.class, () -> applications.saveAndFlush(
                HostApplication.builder().systemUser(user).organizationName("Another")
                        .status(HostApplicationStatus.PENDING).appliedAt(Instant.now()).build()));
        assertEquals(1, applications.count());
    }

    @Test
    void concurrentInitialSubmissionsCreateOnlyOnePendingApplication() throws Exception {
        raceSubmissions();
        assertEquals(1, applications.count());
        assertEquals(HostApplicationStatus.PENDING, service.getCurrent(EMAIL).status());
    }

    @Test
    void deletingLocalUserAlsoRemovesApplicationForExistingStaleUserCleanup() {
        service.apply(EMAIL, new HostApplicationRequestDto("Org", null));
        users.deleteById(user.getUserId());
        assertEquals(0, applications.count());
        assertFalse(users.existsById(user.getUserId()));
    }

    @Test
    void concurrentResubmissionsReuseRejectedRowOnceAndClearReviewFields() throws Exception {
        Instant oldTime = Instant.parse("2020-01-01T00:00:00Z");
        HostApplication rejected = applications.saveAndFlush(HostApplication.builder().systemUser(user)
                .organizationName("Old").status(HostApplicationStatus.REJECTED).appliedAt(oldTime)
                .reviewedAt(oldTime).reviewedBy("reviewer").rejectionReason("Old rejection").build());
        Instant createdAt = service.getCurrent(EMAIL).createdAt();
        raceSubmissions();
        var current = service.getCurrent(EMAIL);
        assertEquals(1, applications.count());
        assertEquals(rejected.getId(), current.id());
        assertEquals(createdAt, current.createdAt());
        assertEquals(HostApplicationStatus.PENDING, current.status());
        assertTrue(current.appliedAt().isAfter(oldTime));
        assertFalse(current.updatedAt().isBefore(current.createdAt()));
        assertNull(current.reviewedAt());
        assertNull(current.reviewedBy());
        assertNull(current.rejectionReason());
    }

    private void raceSubmissions() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> submit = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
            try {
                service.apply(EMAIL, new HostApplicationRequestDto("Org", null));
                return true;
            } catch (DuplicateEntryException conflict) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(submit);
            Future<Boolean> second = executor.submit(submit);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
