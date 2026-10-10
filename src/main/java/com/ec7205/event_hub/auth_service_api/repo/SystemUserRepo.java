package com.ec7205.event_hub.auth_service_api.repo;

import com.ec7205.event_hub.auth_service_api.entity.SystemUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface SystemUserRepo extends JpaRepository<SystemUser,String> {
    public Optional<SystemUser> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from SystemUser u where u.email = :email")
    Optional<SystemUser> findByEmailForUpdate(@Param("email") String email);
}
