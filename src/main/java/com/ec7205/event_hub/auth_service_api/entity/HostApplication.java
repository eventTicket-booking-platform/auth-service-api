package com.ec7205.event_hub.auth_service_api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "host_application")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostApplication {
    @Id
    @Column(length = 80, nullable = false)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "system_user_id", nullable = false, unique = true)
    private SystemUser systemUser;

    @Column(name = "organization_name", length = 200, nullable = false)
    private String organizationName;

    @Column(length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private HostApplicationStatus status;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
