package com.ec7205.event_hub.auth_service_api.repo;

import com.ec7205.event_hub.auth_service_api.entity.SystemAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Optional;

@EnableJpaRepositories
public interface SystemAvatarRepo extends JpaRepository<SystemAvatar,String> {
    @Query(value = "SELECT * FROM system_avatar WHERE user_property_id=?1",nativeQuery = true)
    public Optional<SystemAvatar> findByUserPropertyId(String userPropertyId);
}
