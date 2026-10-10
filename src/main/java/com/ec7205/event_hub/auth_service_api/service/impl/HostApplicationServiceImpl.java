package com.ec7205.event_hub.auth_service_api.service.impl;

import com.ec7205.event_hub.auth_service_api.dto.request.HostApplicationRequestDto;
import com.ec7205.event_hub.auth_service_api.dto.response.HostApplicationResponseDto;
import com.ec7205.event_hub.auth_service_api.entity.HostApplication;
import com.ec7205.event_hub.auth_service_api.entity.HostApplicationStatus;
import com.ec7205.event_hub.auth_service_api.entity.SystemUser;
import com.ec7205.event_hub.auth_service_api.exceptions.BadRequestException;
import com.ec7205.event_hub.auth_service_api.exceptions.DuplicateEntryException;
import com.ec7205.event_hub.auth_service_api.exceptions.EntryNotFoundException;
import com.ec7205.event_hub.auth_service_api.exceptions.UnauthorizedException;
import com.ec7205.event_hub.auth_service_api.repo.HostApplicationRepo;
import com.ec7205.event_hub.auth_service_api.repo.SystemUserRepo;
import com.ec7205.event_hub.auth_service_api.service.HostApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class HostApplicationServiceImpl implements HostApplicationService {
    private final SystemUserRepo users;
    private final HostApplicationRepo applications;

    @Override
    @Transactional
    public HostApplicationResponseDto apply(String authenticatedEmail, HostApplicationRequestDto request) {
        requireEmail(authenticatedEmail);
        String organizationName = request == null || request.getOrganizationName() == null
                ? "" : request.getOrganizationName().trim();
        String reason = request == null || request.getReason() == null ? null : request.getReason().trim();
        if (organizationName.isEmpty() || organizationName.length() > 200) {
            throw new BadRequestException("Organization name is required and must not exceed 200 characters");
        }
        if (reason != null && reason.length() > 2000) {
            throw new BadRequestException("Reason must not exceed 2000 characters");
        }

        // Lock the existing user even when no application exists yet, serializing submissions.
        SystemUser user = users.findByEmailForUpdate(authenticatedEmail)
                .orElseThrow(() -> new EntryNotFoundException("User not found"));
        requireEligibleAccount(user);
        HostApplication application = applications.findBySystemUser_UserId(user.getUserId()).orElse(null);
        if (application != null && application.getStatus() != HostApplicationStatus.REJECTED) {
            throw new DuplicateEntryException("A pending or approved host application already exists");
        }
        if (application == null) application = HostApplication.builder().systemUser(user).build();

        application.setOrganizationName(organizationName);
        application.setReason(reason);
        application.setStatus(HostApplicationStatus.PENDING);
        application.setAppliedAt(Instant.now());
        application.setReviewedAt(null);
        application.setReviewedBy(null);
        application.setRejectionReason(null);
        try {
            return HostApplicationResponseDto.from(applications.saveAndFlush(application));
        } catch (DataIntegrityViolationException exception) {
            // The unique constraint also protects against writers outside this submission path.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                // MySQL duplicate key, or the standard unique-violation state used by H2 tests.
                if (cause instanceof SQLException sql
                        && (sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState()))) {
                    throw new DuplicateEntryException("A host application already exists for this user");
                }
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HostApplicationResponseDto getCurrent(String authenticatedEmail) {
        requireEmail(authenticatedEmail);
        SystemUser user = users.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new EntryNotFoundException("User not found"));
        requireEligibleAccount(user);
        return applications.findBySystemUser_UserId(user.getUserId())
                .map(HostApplicationResponseDto::from)
                .orElseThrow(() -> new EntryNotFoundException("Host application not found"));
    }

    private void requireEmail(String email) {
        if (email == null || email.isBlank()) throw new UnauthorizedException("JWT email is required");
    }

    private void requireEligibleAccount(SystemUser user) {
        if (!user.isActive() || !user.isEnabled() || !user.isEmailVerified()) {
            throw new UnauthorizedException("An active, enabled and email-verified account is required");
        }
    }
}
