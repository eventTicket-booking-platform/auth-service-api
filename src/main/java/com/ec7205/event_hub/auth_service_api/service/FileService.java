package com.ec7205.event_hub.auth_service_api.service;

import com.amazonaws.services.s3.AmazonS3;
import com.ec7205.event_hub.auth_service_api.utils.CommonFileSavedBinaryDataDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


public interface FileService {

    public CommonFileSavedBinaryDataDto createResource(MultipartFile file, String directory,
                                                       String bucket);

    public void deleteResource(String bucket, String directory, String fileName);

    public byte[] downloadFile(String bucket, String fileName);

}
