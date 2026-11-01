package com.maverick.zaslon.gateway.service;

import com.maverick.zaslon.gateway.client.ManagementApiClient;
import com.maverick.zaslon.gateway.dto.responseDto.GatewayRoute;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GatewayConfigurationLoader implements ApplicationRunner {

    private final ManagementApiClient managementApiClient;

    public GatewayConfigurationLoader(ManagementApiClient managementApiClient) {
        this.managementApiClient = managementApiClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        refresh();
    }

    public  void refresh() {

        List<GatewayRoute> routes = managementApiClient.fetchActiveRoutes();

        for (GatewayRoute route : routes) {
            System.out.println("Route Target path prefix ==> " + route.getTargetPathPrefix());
        }
    }
}
