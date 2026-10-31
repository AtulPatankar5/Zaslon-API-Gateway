package com.maverick.zaslon.management.service;

import com.maverick.zaslon.management.entity.Organization;
import com.maverick.zaslon.management.entity.OrganizationStatus;
import com.maverick.zaslon.management.Exception.ApiException;
import com.maverick.zaslon.management.repository.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {
    private final OrganizationRepository organizationRepository;

    public TenantContext(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Organization getOrganization() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tenant Identity is Missing");
        }
        Long organizationId = ((JwtAuthenticationToken) authentication).getToken().getClaim("organizationId");

        Organization organization = organizationRepository.findById(organizationId).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Organization Id not found"));
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Organization Status is not Active");
        }
        return organization;
    }
}
