package com.ec7205.event_hub.auth_service_api.api;

import com.ec7205.event_hub.auth_service_api.config.JwtService;
import com.ec7205.event_hub.auth_service_api.service.SystemAvatarService;
import com.ec7205.event_hub.auth_service_api.utils.StandardResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;

@RestController
@RequestMapping("/user-service/api/v1/avatars")
@RequiredArgsConstructor
public class SystemAvatarController {

    private final SystemAvatarService avatarService;
    private final JwtService jwtService;

    @PostMapping("/user/manage-avatar")
    @PreAuthorize("hasAnyRole('user')")
    public ResponseEntity<StandardResponseDto> manageAvatar(
            @RequestHeader("Authorization") String tokenHeader,
            @RequestParam("avatar") MultipartFile avatar) throws SQLException, JsonProcessingException {
//        Use token to identify the user
        String token = tokenHeader.replace("Bearer ", "");
        String email = jwtService.getEmail(token);
/*
        ObjectMapper objectMapper = new ObjectMapper();
        RequestSystemUserAvatarDto dto = objectMapper.readValue(data, RequestSystemUserAvatarDto.class);*/
        avatarService.createSystemUserAvatar(avatar, email);
        return new ResponseEntity<>(
                new StandardResponseDto(
                        201, "Avatar was Updated", null
                ),
                HttpStatus.CREATED
        );
    }
}
