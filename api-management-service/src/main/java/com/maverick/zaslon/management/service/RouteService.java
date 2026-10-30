package com.maverick.zaslon.management.service;

import com.maverick.zaslon.management.Entity.*;
import com.maverick.zaslon.management.Exception.ApiException;
import com.maverick.zaslon.management.dto.requestDto.HeaderRuleRequest;
import com.maverick.zaslon.management.dto.requestDto.RouteRequest;
import com.maverick.zaslon.management.dto.requestDto.RouteStatusRequest;
import com.maverick.zaslon.management.dto.responseDto.AuditRecordResponse;
import com.maverick.zaslon.management.dto.responseDto.RouteResponse;
import com.maverick.zaslon.management.dto.responseDto.RouteVersionResponse;
import com.maverick.zaslon.management.repository.ApiRouteRepository;
import com.maverick.zaslon.management.repository.AuditRecordRepository;
import com.maverick.zaslon.management.repository.RouteConfigurationVersionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RouteService {

    private final ApiRouteRepository apiRouteRepository;
    private final TenantContext tenantContext;
    private final ObjectMapper objectMapper;
    private final RouteConfigurationVersionRepository versionRepository;
    private final AuditService auditService;
    private final AuditRecordRepository auditRecordRepository;

    public RouteService(ApiRouteRepository apiRouteRepository,
                        TenantContext tenantContext,
                        ObjectMapper objectMapper,
                        RouteConfigurationVersionRepository routeConfigurationVersionRepository,
                        AuditService auditService,
                        AuditRecordRepository auditRecordRepository
    ) {
        this.apiRouteRepository = apiRouteRepository;
        this.tenantContext = tenantContext;
        this.objectMapper = objectMapper;
        this.versionRepository = routeConfigurationVersionRepository;
        this.auditService = auditService;
        this.auditRecordRepository = auditRecordRepository;
    }

    @Transactional
    public RouteResponse create(@Valid RouteRequest request) {
        long startedAt = System.nanoTime();
        Organization organization = tenantContext.getOrganization();
        // Validation
        validate(request, organization);
        if (apiRouteRepository.existsByOrganizationIdAndRouteId(organization.getId(), request.getRouteId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Route ID already exists for this organization");
        }

        ApiRoute route = new ApiRoute();
        route.setOrganization(organization);
        route.setConfigurationVersion(1L);
        route.setCreatedAt(LocalDateTime.now());
        apply(route, request);
        apiRouteRepository.save(route);

        saveVersion(route, "CREATED");
        auditService.record(organization, "CREATE_ROUTE",
                "API_ROUTE", route.getRouteId(), startedAt,
                "Route configuration version 1 created");
        return RouteResponse.from(route);

    }

    @Transactional(readOnly = true)
    public List<RouteResponse> findAllTenant() {
        Organization organization = tenantContext.getOrganization();
        return toResponses(apiRouteRepository.findAllByOrganizationId(organization.getId()));
    }

    @Transactional(readOnly = true)
    public RouteResponse findById(Long id) {
        Organization organization = tenantContext.getOrganization();
        ApiRoute apiRoute = getOwnedRoute(id, organization.getId());
        return RouteResponse.from(apiRoute);
    }


    @Transactional
    public RouteResponse update(Long id, RouteRequest request) {
        Long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();
        ApiRoute route = getOwnedRoute(id, organization.getId());

        if (!route.getRouteId().equals(request.getRouteId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "routeId cannot be changed");
        }
        validate(request, organization);

        route.setConfigurationVersion(route.getConfigurationVersion() + 1);
        apply(route, request);

        saveVersion(route, "UPDATED");

        auditService.record(organization, "UPDATE_ROUTE", "API_ROUTE",
                route.getRouteId(), startedAt, "Route configuration version " +
                        route.getConfigurationVersion() + " created");

        return RouteResponse.from(route);
    }

    @Transactional
    public void delete(Long id) {
        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();
        ApiRoute route = getOwnedRoute(id, organization.getId());
        route.setConfigurationVersion(route.getConfigurationVersion() + 1);
        route.setUpdatedAt(LocalDateTime.now());

        saveVersion(route, "DELETED");

        auditService.record(organization, "DELETE_ROUTE", "API_ROUTE", route.getRouteId(), startedAt,
                "Final configuration saved as version " + route.getConfigurationVersion());


        apiRouteRepository.delete(route);
    }


    @Transactional
    public RouteResponse changeStatus(Long id, RouteStatusRequest request) {
        long startedAt = System.nanoTime();

        Organization organization = tenantContext.getOrganization();
        ApiRoute route = getOwnedRoute(id, organization.getId());

        route.setActive(request.getActive());
        route.setConfigurationVersion(route.getConfigurationVersion() + 1);
        route.setUpdatedAt(LocalDateTime.now());

        String changeType = request.getActive() ? "ACTIVATED" : "DEACTIVATED";
        saveVersion(route, changeType);


        auditService.record(organization, "CHANGE_STATUS", "API_ROUTE", route.getRouteId(), startedAt,
                "Route configuration version " + route.getConfigurationVersion() + " created");

        return RouteResponse.from(route);
    }


    // gateway Methods
    @Transactional(readOnly = true)
    public List<RouteResponse> findAllActiveForGateway() {
        return toResponses(
                apiRouteRepository.findAllByActiveTrueAndOrganizationStatus(OrganizationStatus.ACTIVE)
        );
    }

    private ApiRoute getOwnedRoute(Long id, Long organizationId) {
        return apiRouteRepository.findByIdAndOrganizationId(id, organizationId).
                orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Route not found"));
    }


    private void apply(ApiRoute route, RouteRequest request) {
        route.setRouteId(request.getRouteId());
        route.setName(request.getName());
        route.setPathPattern(request.getPathPattern());
        route.setTargetBaseUrl(request.getTargetBaseUrl());
        route.setTargetPathPrefix(request.getTargetPathPrefix());
        route.setRequestsPerMinute(request.getRequestsPerMinute());
        route.setResponseTimeoutMs(request.getResponseTimeoutMs());
        route.setIdempotencyEnabled(request.isIdempotencyEnabled());
        route.setActive(request.isActive());
        route.setUpdatedAt(LocalDateTime.now());
        route.setAllowedMethods(request.getAllowedMethods());

        route.getHeaderRules().clear();
        for (HeaderRuleRequest headerRuleRequest : request.getHeaderRules()) {
            RouteHeaderRule rule = new RouteHeaderRule();
            rule.setRoute(route);
            rule.setDirection(headerRuleRequest.getDirection().toUpperCase(Locale.ENGLISH));
            rule.setAction(headerRuleRequest.getAction().toUpperCase(Locale.ENGLISH));
            rule.setHeaderName(headerRuleRequest.getHeaderName());
            rule.setHeaderValue(headerRuleRequest.getHeaderValue());
            rule.setCreatedAt(LocalDateTime.now());
            rule.setUpdatedAt(LocalDateTime.now());
            route.getHeaderRules().add(rule);
        }
    }

    private void validate(RouteRequest request, Organization organization) {
        String tenantPrefix = "/" + organization.getSlug() + "/";

        if (!request.getPathPattern().startsWith(tenantPrefix)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "pathPattern must start with tenant Prefix : " + tenantPrefix);
        }
        if (!request.getTargetPathPrefix().startsWith("/")) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "pathPattern must start with tenant Prefix : /");
        }
        try {
            URI uri = URI.create(request.getTargetBaseUrl());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "targetbaseURL must be valid HTTP or HTTPS URL");
        }

        normalizeMethods(request.getAllowedMethods());
        for (HeaderRuleRequest rule : request.getHeaderRules()) {
            String direction = rule.getDirection().toUpperCase(Locale.ENGLISH);
            String action = rule.getAction().toUpperCase(Locale.ENGLISH);
            if (!"REQUEST".equals(direction) && !"RESPONSE".equals(direction)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Header direction must be REQUEST or RESPONSE");
            }
            if (!"ADD".equals(action) && !"REMOVE".equals(action)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Header action must be ADD or REMOVE");
            }

            if ("ADD".equals(action) && (rule.getHeaderValue() == null || rule.getHeaderValue().trim().isEmpty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Add header rule required header value");
            }
        }

    }

    private Set<String> normalizeMethods(Set<String> allowedMethods) {
        Set<String> normalizedMethods = new HashSet<>();
        for (String methods : allowedMethods) {
            String value = methods.toUpperCase(Locale.ENGLISH);
            try {
                HttpMethod.valueOf(value);
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported HTTP method : " + value);
            }
            normalizedMethods.add(value);
        }
        return normalizedMethods;

    }


    private List<RouteResponse> toResponses(List<ApiRoute> routes) {
        List<RouteResponse> routeResponses = new ArrayList<>();

        for (ApiRoute route : routes) {
            routeResponses.add(RouteResponse.from(route));
        }
        return routeResponses;
    }

    private void saveVersion(ApiRoute route, String changeType) {
        RouteConfigurationVersion version = new RouteConfigurationVersion();
        version.setOrganization(route.getOrganization());
        version.setRouteId(route.getRouteId());
        version.setChangeType(changeType);
        version.setConfigurationSnapshot(objectMapper.writeValueAsString(RouteResponse.from(route)));
        version.setVersionNumber(route.getConfigurationVersion());
        version.setCreatedAt(LocalDateTime.now());

        versionRepository.save(version);
    }

    public List<RouteVersionResponse> getVersions(String routeId) {
        Organization organization = tenantContext.getOrganization();
        List<RouteVersionResponse> response = new ArrayList<>();

        List<RouteConfigurationVersion> versions = versionRepository.
                findAllByOrganizationIdAndRouteIdOrderByVersionNumberDesc(organization.getId(), routeId);

        for (RouteConfigurationVersion version : versions) {
            response.add(RouteVersionResponse.from(version));
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<AuditRecordResponse> getAuditRecords() {

        Organization organization = tenantContext.getOrganization();

        List<AuditRecordResponse> response = new ArrayList<>();

        List<ControlPlaneAuditRecord> controlPlaneAuditRecords = auditRecordRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organization.getId());

        for (ControlPlaneAuditRecord controlPlaneAuditRecord : controlPlaneAuditRecords) {
            response.add(AuditRecordResponse.from(controlPlaneAuditRecord));
        }
        return response;
    }
}
