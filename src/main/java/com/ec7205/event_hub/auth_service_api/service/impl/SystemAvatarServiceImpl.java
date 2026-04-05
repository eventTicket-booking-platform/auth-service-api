package com.ec7205.event_hub.auth_service_api.service.impl;

import com.amazonaws.services.accessanalyzer.model.InternalServerException;
import com.ec7205.event_hub.auth_service_api.entity.SystemAvatar;
import com.ec7205.event_hub.auth_service_api.entity.SystemUser;
import com.ec7205.event_hub.auth_service_api.exceptions.EntryNotFoundException;
import com.ec7205.event_hub.auth_service_api.repo.SystemAvatarRepo;
import com.ec7205.event_hub.auth_service_api.repo.SystemUserRepo;
import com.ec7205.event_hub.auth_service_api.service.FileService;
import com.ec7205.event_hub.auth_service_api.service.SystemAvatarService;
import com.ec7205.event_hub.auth_service_api.utils.CommonFileSavedBinaryDataDto;
import com.ec7205.event_hub.auth_service_api.utils.FileDataExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemAvatarServiceImpl implements SystemAvatarService {

    private final SystemAvatarRepo systemUserAvatarRepo;
    private final SystemUserRepo systemUserRepo;
    private final FileService fileService;
    private final FileDataExtractor fileDataExtractor;

    @Value("${bucketName}")
    private String bucketName;

    @Override
    public void createSystemUserAvatar(MultipartFile file,String email) throws SQLException {
        CommonFileSavedBinaryDataDto resource = null;
        Optional<SystemUser> selectedUser = systemUserRepo.findByEmail(email);
        if (selectedUser.isEmpty()) {
            throw new EntryNotFoundException("User not found.");
        }
        Optional<SystemAvatar> selectedAvatar = systemUserAvatarRepo.findByUserPropertyId(selectedUser.get().getUserId());
        if (selectedAvatar.isPresent()) {
            try {
                try {

                    // Delete the existing avatar resource directory
                    fileService.deleteResource(bucketName, "avatar/" + selectedUser.get().getUserId() + "/resource/",fileDataExtractor.byteArrayToString(selectedAvatar.get().getFileName()));
                } catch (Exception e) {
                    // Handle deletion failure if needed
                    throw new InternalServerException("Failed to delete existing avatar resource directory");
                }

                resource = fileService.createResource(file, "avatar/" + selectedUser.get().getUserId() + "/resource/", bucketName);

                selectedAvatar.get().setCreatedDate(new Date());
                selectedAvatar.get().setDirectory(resource.getDirectory().getBytes());
                selectedAvatar.get().setFileName(fileDataExtractor.blobToByteArray(resource.getFileName()));
                selectedAvatar.get().setHash(fileDataExtractor.blobToByteArray(resource.getHash()));
                selectedAvatar.get().setResourceUrl(fileDataExtractor.blobToByteArray(resource.getResourceUrl()));

                systemUserAvatarRepo.save(selectedAvatar.get());

            } catch (Exception e) {
                assert resource != null;
                fileService.deleteResource(bucketName,
                        resource.getDirectory(), fileDataExtractor.extractActualFileName(
                                new InputStreamReader(
                                        resource.getFileName().getBinaryStream())));
                fileService.deleteResource(bucketName, "avatar/" + selectedUser.get().getUserId() + "/resource/",fileDataExtractor.byteArrayToString(selectedAvatar.get().getFileName()));
                throw new InternalServerException("Something went wrong");
            }
        } else {
            // save
            try {
                resource = fileService.createResource(file, "avatar/" + selectedUser.get().getUserId() + "/resource/", bucketName);
                SystemAvatar buildAvatar = SystemAvatar.builder()
                        .propertyId(UUID.randomUUID().toString())
                        .createdDate(new Date())
                        .directory(resource.getDirectory().getBytes())
                        .fileName(fileDataExtractor.blobToByteArray(resource.getFileName()))
                        .hash(fileDataExtractor.blobToByteArray(resource.getHash()))
                        .resourceUrl(fileDataExtractor.blobToByteArray(resource.getResourceUrl()))
                        .systemUser(selectedUser.get()).build();
                systemUserAvatarRepo.save(buildAvatar);
            } catch (Exception e) {
                System.out.println(e);
                assert resource != null;
                fileService.deleteResource(bucketName,
                        resource.getDirectory(), fileDataExtractor.extractActualFileName(
                                new InputStreamReader(
                                        resource.getFileName().getBinaryStream())));
                throw new InternalServerException("Something went wrong");
            }
        }
    }
}
