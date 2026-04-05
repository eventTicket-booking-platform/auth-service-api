package com.ec7205.event_hub.auth_service_api.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Blob;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommonFileSavedBinaryDataDto {
    private Blob hash;
    private String directory;
    private Blob fileName;
    private Blob resourceUrl;
}
