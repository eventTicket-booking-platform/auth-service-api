package com.ec7205.event_hub.auth_service_api;

import com.ec7205.event_hub.auth_service_api.dto.request.HostApplicationRequestDto;
import com.ec7205.event_hub.auth_service_api.entity.*;
import com.ec7205.event_hub.auth_service_api.exceptions.*;
import com.ec7205.event_hub.auth_service_api.repo.HostApplicationRepo;
import com.ec7205.event_hub.auth_service_api.repo.SystemUserRepo;
import com.ec7205.event_hub.auth_service_api.service.impl.HostApplicationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HostApplicationServiceTest {
    private static final String EMAIL = "customer@example.test";
    @Mock SystemUserRepo users;
    @Mock HostApplicationRepo applications;
    @InjectMocks HostApplicationServiceImpl service;
    private SystemUser user;

    @BeforeEach
    void user() {
        user = SystemUser.builder().userId("customer-id").email(EMAIL)
                .isActive(true).isEnabled(true).isEmailVerified(true).build();
    }

    @Test
    void verifiedUserAppliesAsPendingWithTrimmedFields() {
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(applications.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.apply(EMAIL, new HostApplicationRequestDto("  Organization  ", "  Events  "));
        assertEquals(HostApplicationStatus.PENDING, response.status());
        assertEquals("Organization", response.organizationName());
        assertEquals("Events", response.reason());
        assertNotNull(response.appliedAt());
        ArgumentCaptor<HostApplication> saved = ArgumentCaptor.forClass(HostApplication.class);
        verify(applications).saveAndFlush(saved.capture());
        assertSame(user, saved.getValue().getSystemUser());
        assertNull(saved.getValue().getReviewedAt());
        assertNull(saved.getValue().getReviewedBy());
        assertNull(saved.getValue().getRejectionReason());
    }

    @ParameterizedTest
    @EnumSource(value = HostApplicationStatus.class, names = {"PENDING", "APPROVED"})
    void pendingAndApprovedApplicationsConflict(HostApplicationStatus status) {
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(applications.findBySystemUser_UserId("customer-id"))
                .thenReturn(Optional.of(HostApplication.builder().status(status).build()));
        assertThrows(DuplicateEntryException.class,
                () -> service.apply(EMAIL, new HostApplicationRequestDto("Org", null)));
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void rejectedApplicationReusesRowAndClearsReviewMetadata() {
        Instant previous = Instant.parse("2020-01-01T00:00:00Z");
        HostApplication application = HostApplication.builder().id("existing-application").systemUser(user)
                .status(HostApplicationStatus.REJECTED).organizationName("Old")
                .appliedAt(previous).createdAt(previous).reviewedAt(previous)
                .reviewedBy("reviewer").rejectionReason("Old reason").build();
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(applications.findBySystemUser_UserId("customer-id")).thenReturn(Optional.of(application));
        when(applications.saveAndFlush(application)).thenReturn(application);
        var response = service.apply(EMAIL, new HostApplicationRequestDto("New", null));
        assertEquals("existing-application", response.id());
        assertEquals(HostApplicationStatus.PENDING, response.status());
        assertEquals("New", response.organizationName());
        assertTrue(response.appliedAt().isAfter(previous));
        assertEquals(previous, response.createdAt());
        assertNull(response.reviewedAt());
        assertNull(response.reviewedBy());
        assertNull(response.rejectionReason());
        assertNull(response.reason());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void organizationIsRequired(String organization) {
        assertThrows(BadRequestException.class,
                () -> service.apply(EMAIL, new HostApplicationRequestDto(organization, null)));
        verifyNoInteractions(users, applications);
    }

    @Test
    void rejectsOverlongFields() {
        assertThrows(BadRequestException.class,
                () -> service.apply(EMAIL, new HostApplicationRequestDto("x".repeat(201), null)));
        assertThrows(BadRequestException.class,
                () -> service.apply(EMAIL, new HostApplicationRequestDto("Org", "x".repeat(2001))));
        verifyNoInteractions(users, applications);
    }

    @Test
    void missingJwtEmailAndMissingLocalUserAreRejected() {
        assertThrows(UnauthorizedException.class,
                () -> service.apply(null, new HostApplicationRequestDto("Org", null)));
        assertThrows(EntryNotFoundException.class,
                () -> service.apply(EMAIL, new HostApplicationRequestDto("Org", null)));
        verifyNoInteractions(applications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "disabled", "unverified"})
    void accountMustBeActiveEnabledAndVerified(String state) {
        if (state.equals("inactive")) user.setActive(false);
        if (state.equals("disabled")) user.setEnabled(false);
        if (state.equals("unverified")) user.setEmailVerified(false);
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        assertThrows(UnauthorizedException.class,
                () -> service.apply(EMAIL, new HostApplicationRequestDto("Org", null)));
        verifyNoInteractions(applications);
    }

    @Test
    void currentApplicationIsScopedToLocalUserAndMissingApplicationIsNotFound() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        assertThrows(EntryNotFoundException.class, () -> service.getCurrent(EMAIL));
        verify(applications).findBySystemUser_UserId("customer-id");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1062,23000", "23505,23505"})
    void duplicateConstraintRaceIsTranslatedToConflict(int errorCode, String sqlState) {
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        var constraint = new SQLException("duplicate", sqlState, errorCode);
        when(applications.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate", constraint));
        assertThrows(DuplicateEntryException.class,
                () -> service.apply(EMAIL, new HostApplicationRequestDto("Org", null)));
    }

    @Test
    void unrelatedDatabaseFailureIsNotMisreportedAsDuplicate() {
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(applications.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Other constraint"));
        assertThrows(DataIntegrityViolationException.class,
                () -> service.apply(EMAIL, new HostApplicationRequestDto("Org", null)));
    }
}
