package com.ec7205.event_hub.auth_service_api.service.impl;

import com.ec7205.event_hub.auth_service_api.config.KeycloakSecurityUtil;
import com.ec7205.event_hub.auth_service_api.dto.response.AdminHostApplicationResponseDto;
import com.ec7205.event_hub.auth_service_api.dto.response.pagination.HostApplicationPageResponseDto;
import com.ec7205.event_hub.auth_service_api.entity.HostApplication;
import com.ec7205.event_hub.auth_service_api.entity.HostApplicationStatus;
import com.ec7205.event_hub.auth_service_api.exceptions.BadRequestException;
import com.ec7205.event_hub.auth_service_api.exceptions.DuplicateEntryException;
import com.ec7205.event_hub.auth_service_api.exceptions.EntryNotFoundException;
import com.ec7205.event_hub.auth_service_api.exceptions.UnauthorizedException;
import com.ec7205.event_hub.auth_service_api.repo.HostApplicationRepo;
import com.ec7205.event_hub.auth_service_api.service.AdminHostApplicationService;
import com.ec7205.event_hub.auth_service_api.messaging.AuthNotificationEventPublisher;
import com.ec7205.event_hub.auth_service_api.messaging.dto.AuthNotificationEvent;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminHostApplicationServiceImpl implements AdminHostApplicationService {
    private static final Logger logger = LoggerFactory.getLogger(AdminHostApplicationServiceImpl.class);
    private final HostApplicationRepo applications;
    private final KeycloakSecurityUtil keycloakUtil;
    private final AuthNotificationEventPublisher notificationPublisher;

    @Value("${keycloak.config.realm}")
    private String realm;

    @Override
    @Transactional(readOnly = true)
    public HostApplicationPageResponseDto list(HostApplicationStatus status, Pageable pageable) {
        var page = status == null ? applications.findAll(pageable) : applications.findByStatus(status, pageable);
        return new HostApplicationPageResponseDto(
                page.getContent().stream().map(AdminHostApplicationResponseDto::from).toList(), page.getTotalElements());
    }

    @Override
    @Transactional
    public AdminHostApplicationResponseDto approve(String applicationId, String adminId) {
        requireAdminId(adminId);
        HostApplication application = pendingApplication(applicationId);
        String keycloakId = application.getSystemUser().getKeycloakId();
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new EntryNotFoundException("Applicant has no Keycloak user ID");
        }
        var keycloakRealm = keycloakUtil.getKeycloakInstance().realm(realm);
        var user = keycloakRealm.users().get(keycloakId);
        try {
            user.toRepresentation(); // Verify the existing account; never create another user.
        } catch (NotFoundException exception) {
            throw new EntryNotFoundException("Applicant's Keycloak user was not found");
        }
        RoleScopeResource roles = user.roles().realmLevel();
        boolean alreadyHost = roles.listAll().stream().anyMatch(role -> "host".equals(role.getName()));
        if (!alreadyHost) {
            RoleRepresentation host = keycloakRealm.roles().get("host").toRepresentation();
            roles.add(List.of(host));
            compensateHostGrantOnRollback(applicationId, keycloakId, roles, host);
        }

        application.setStatus(HostApplicationStatus.APPROVED);
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(adminId);
        application.setRejectionReason(null);
        HostApplication saved = applications.saveAndFlush(application);
        publishReviewAfterCommit(saved, "HOST_APPLICATION_APPROVED");
        return AdminHostApplicationResponseDto.from(saved);
    }

    @Override
    @Transactional
    public AdminHostApplicationResponseDto reject(String applicationId, String adminId, String reason) {
        requireAdminId(adminId);
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2000) {
            throw new BadRequestException("Rejection reason is required and must not exceed 2000 characters");
        }
        HostApplication application = pendingApplication(applicationId);
        application.setStatus(HostApplicationStatus.REJECTED);
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(adminId);
        application.setRejectionReason(trimmed);
        HostApplication saved = applications.saveAndFlush(application);
        publishReviewAfterCommit(saved, "HOST_APPLICATION_REJECTED");
        return AdminHostApplicationResponseDto.from(saved);
    }

    private void publishReviewAfterCommit(HostApplication application, String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", application.getSystemUser().getEmail());
        payload.put("firstName", application.getSystemUser().getFirstName());
        payload.put("organizationName", application.getOrganizationName());
        if ("HOST_APPLICATION_REJECTED".equals(type)) {
            payload.put("rejectionReason", application.getRejectionReason());
        }
        AuthNotificationEvent event = AuthNotificationEvent.builder().type(type).payload(payload).build();
        String applicationId = application.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notificationPublisher.publish(event);
                } catch (RuntimeException exception) {
                    // The review is committed; a RabbitMQ failure cannot roll it back.
                    logger.error("Failed to publish {} for committed host application {}; notification reconciliation required",
                            type, applicationId, exception);
                }
            }
        });
    }

    private HostApplication pendingApplication(String applicationId) {
        HostApplication application = applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new EntryNotFoundException("Host application not found"));
        if (application.getStatus() != HostApplicationStatus.PENDING) {
            throw new DuplicateEntryException("Only pending host applications can be reviewed");
        }
        return application;
    }

    private void requireAdminId(String adminId) {
        if (adminId == null || adminId.isBlank() || adminId.length() > 80) {
            throw new UnauthorizedException("A valid admin JWT subject is required");
        }
    }

    private void compensateHostGrantOnRollback(String applicationId, String keycloakId,
                                              RoleScopeResource roles, RoleRepresentation host) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        roles.remove(List.of(host));
                        logger.warn("Removed newly granted host role after DB rollback for application {} and Keycloak user {}",
                                applicationId, keycloakId);
                    } catch (RuntimeException exception) {
                        logger.error("Failed to compensate host role for application {} and Keycloak user {}; reconciliation required",
                                applicationId, keycloakId, exception);
                    }
                } else if (status == STATUS_UNKNOWN) {
                    logger.error("Unknown DB completion for application {} and Keycloak user {}; verify approval and role manually",
                            applicationId, keycloakId);
                }
            }
        });
    }
}
