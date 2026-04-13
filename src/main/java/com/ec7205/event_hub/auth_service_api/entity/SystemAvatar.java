package com.ec7205.event_hub.auth_service_api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "system_avatar")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class SystemAvatar {

    @Id
    @Column(name = "property_id", nullable = false, length = 80)
    private String propertyId;

    @Lob
    @Column(name = "directory", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] directory;

    @Lob
    @Column(name = "file_name", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] fileName;

    @Lob
    @Column(name = "resource_url", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] resourceUrl;

    @Lob
    @Column(name = "hash", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] hash;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_date", nullable = false, columnDefinition = "DATETIME")
    private Date createdDate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_property_id",referencedColumnName ="user_id",nullable = false,unique = true )
    private SystemUser systemUser;

}
