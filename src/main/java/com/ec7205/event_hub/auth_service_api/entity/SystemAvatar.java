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
    @Column(name = "directory", nullable = false)
    private byte[] directory;

    @Lob
    @Column(name = "file_name", nullable = false)
    private byte[] fileName;

    @Lob
    @Column(name = "resource_url", nullable = false)
    private byte[] resourceUrl;

    @Lob
    @Column(name = "hash", nullable = false)
    private byte[] hash;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_date", nullable = false, columnDefinition = "DATETIME")
    private Date createdDate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_property_id",referencedColumnName ="user_id",nullable = false,unique = true )
    private SystemUser systemUser;

}
