package com.ec7205.event_hub.auth_service_api.repo;

import com.ec7205.event_hub.auth_service_api.entity.SystemUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemUserRepo extends JpaRepository<SystemUser,String> {
    public Optional<SystemUser> findByEmail(String email);
}
