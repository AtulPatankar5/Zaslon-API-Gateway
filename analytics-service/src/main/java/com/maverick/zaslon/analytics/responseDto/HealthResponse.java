package com.maverick.zaslon.analytics.responseDto;

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
}
