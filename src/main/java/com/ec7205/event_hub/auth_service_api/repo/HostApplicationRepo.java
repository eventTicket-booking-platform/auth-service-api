package com.ec7205.event_hub.auth_service_api.repo;

import com.ec7205.event_hub.auth_service_api.entity.HostApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HostApplicationRepo extends JpaRepository<HostApplication, String> {
    Optional<HostApplication> findBySystemUser_UserId(String userId);
}
