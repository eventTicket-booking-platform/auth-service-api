package com.ec7205.event_hub.auth_service_api.repo;

import com.ec7205.event_hub.auth_service_api.entity.HostApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ec7205.event_hub.auth_service_api.entity.HostApplicationStatus;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface HostApplicationRepo extends JpaRepository<HostApplication, String> {
    Optional<HostApplication> findBySystemUser_UserId(String userId);

    @Override
    @EntityGraph(attributePaths = "systemUser")
    Page<HostApplication> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "systemUser")
    Page<HostApplication> findByStatus(HostApplicationStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from HostApplication a where a.id = :id")
    Optional<HostApplication> findByIdForUpdate(@Param("id") String id);
}
