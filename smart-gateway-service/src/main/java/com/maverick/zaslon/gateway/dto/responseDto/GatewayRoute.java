package com.maverick.zaslon.gateway.dto.responseDto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@Getter
public class GatewayRoute {

    private Long id;
    private Long organizationId;
    private String routeId;
    private String name;
    private String pathPattern;
    private String targetBaseUrl;
    private String targetPathPrefix;
    private Integer requestsPerMinute;
    private Integer responseTimeoutMs;
    private boolean idempotencyEnabled;
    private boolean active;
    private Long configurationVersion;
    private Set<String> allowedMethods;
    private List<HealthResponse.HeaderRules> headerRules;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


}

