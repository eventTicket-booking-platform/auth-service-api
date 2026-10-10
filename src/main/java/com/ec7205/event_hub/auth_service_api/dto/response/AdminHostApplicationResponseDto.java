package com.ec7205.event_hub.auth_service_api.dto.response;

import com.ec7205.event_hub.auth_service_api.entity.HostApplication;
import com.ec7205.event_hub.auth_service_api.entity.HostApplicationStatus;
import com.ec7205.event_hub.auth_service_api.entity.SystemUser;

import java.time.Instant;

public record AdminHostApplicationResponseDto(
        String applicationId, String userId, String firstName, String lastName,
        String email, String contact, String organizationName, String reason,
        HostApplicationStatus status, Instant appliedAt, Instant reviewedAt,
        String reviewedBy, String rejectionReason
) {
    public static AdminHostApplicationResponseDto from(HostApplication application) {
        SystemUser user = application.getSystemUser();
        return new AdminHostApplicationResponseDto(application.getId(), user.getUserId(),
                user.getFirstName(), user.getLastName(), user.getEmail(), user.getContact(),
                application.getOrganizationName(), application.getReason(), application.getStatus(),
                application.getAppliedAt(), application.getReviewedAt(), application.getReviewedBy(),
                application.getRejectionReason());
    }
}
