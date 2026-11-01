package com.maverick.zaslon.gateway.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Setter
@Getter
public class HealthResponse {
    private String serviceName;
    private String serviceDescription;
    private String appStatus;

    @Getter
    @AllArgsConstructor
    @Setter
    public static class HeaderRules {


        private Long id;
        private String direction;
        private String action;
        private String headerName;
        private String headerValue;

    }
}
