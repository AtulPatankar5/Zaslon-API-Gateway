package com.maverick.zaslon.management.controller;

import com.maverick.zaslon.management.dto.requestDto.TenantOrganizationRequest;
import com.maverick.zaslon.management.dto.responseDto.OrganizationResponse;
import com.maverick.zaslon.management.dto.responseDto.TenantAdminResponse;
import com.maverick.zaslon.management.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/organization")
public class TenantController {

    private final OrganizationService organizationService;

    public TenantController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    //    Get Organization
    @GetMapping
    public ResponseEntity<OrganizationResponse> getOwnOrganization() {
        return ResponseEntity.ok(organizationService.getOwnOrganization());
    }

    //    update name of organization
    @PutMapping
    public ResponseEntity<OrganizationResponse> updateOwnOrganization(@Valid @RequestBody TenantOrganizationRequest request) {
        return ResponseEntity.ok(organizationService.updateOwnOrganization(request));
    }


}
