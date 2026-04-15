package com.ec7205.event_hub.auth_service_api.dto.response.pagination;


import com.ec7205.event_hub.auth_service_api.dto.response.ResponseUserDetailsDto;
import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDetailsPaginateResponseDto {

    private List<ResponseUserDetailsDto> dataList;
    private long dataCount;
}
