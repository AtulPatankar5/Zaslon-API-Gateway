package com.maverick.zaslon.gateway.events;

import com.maverick.zaslon.gateway.service.GatewayConfigurationLoader;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationEventConsumer {

    private final GatewayConfigurationLoader gatewayConfigurationLoader;


    public ConfigurationEventConsumer(GatewayConfigurationLoader gatewayConfigurationLoader) {
        this.gatewayConfigurationLoader = gatewayConfigurationLoader;
    }

    @KafkaListener(topics = "${gateway.kafka.configuration-topic}",
            groupId = "${gateway.kafka.configuration-group}")
    public void configurationChanged(String event) {
        System.out.println("ConfigurationEventConsumer.configurationChanged: " + event);
        gatewayConfigurationLoader.refresh();

        System.out.println("ConfigurationEventConsumer.configurationChanged: " + event);
    }
}
