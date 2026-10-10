package com.ec7205.event_hub.auth_service_api.dto.response;

import com.ec7205.event_hub.auth_service_api.entity.HostApplication;
import com.ec7205.event_hub.auth_service_api.entity.HostApplicationStatus;

import java.time.Instant;

public record HostApplicationResponseDto(
        String id,
        String organizationName,
        String reason,
        HostApplicationStatus status,
        Instant appliedAt,
        Instant reviewedAt,
        String reviewedBy,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static HostApplicationResponseDto from(HostApplication application) {
        return new HostApplicationResponseDto(
                application.getId(), application.getOrganizationName(), application.getReason(),
                application.getStatus(), application.getAppliedAt(), application.getReviewedAt(),
                application.getReviewedBy(), application.getRejectionReason(),
                application.getCreatedAt(), application.getUpdatedAt());
    }
}
