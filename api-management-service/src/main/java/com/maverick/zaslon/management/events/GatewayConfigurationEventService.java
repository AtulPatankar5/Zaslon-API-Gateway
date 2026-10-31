package com.maverick.zaslon.management.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class GatewayConfigurationEventService {

    private final ApplicationEventPublisher eventPublisher;

    public GatewayConfigurationEventService(ApplicationEventPublisher applicationEventPublisher) {
        this.eventPublisher = applicationEventPublisher;
    }

    // this will publish local event to be triggered .
    public void publish(String changeType, long organizationId, String routeId, Long configurationVersion) {
        eventPublisher.publishEvent(new GatewayConfigurationChangeEvent(
                UUID.randomUUID().toString(), "GATEWAY_CONFIGURTION_CHANGED", changeType
                , organizationId, routeId, configurationVersion, LocalDateTime.now()
        ));
    }
}
