package com.maverick.zaslon.management.service;

import com.maverick.zaslon.management.Entity.ManagementUser;
import com.maverick.zaslon.management.Entity.Organization;
import com.maverick.zaslon.management.Entity.OrganizationStatus;
import com.maverick.zaslon.management.Entity.Role;
import com.maverick.zaslon.management.Exception.ApiException;
import com.maverick.zaslon.management.dto.requestDto.OrganizationRequest;
import com.maverick.zaslon.management.dto.requestDto.OrganizationStatusRequest;
import com.maverick.zaslon.management.dto.requestDto.TenantAdminRequest;
import com.maverick.zaslon.management.dto.requestDto.TenantOrganizationRequest;
import com.maverick.zaslon.management.dto.responseDto.OrganizationResponse;
import com.maverick.zaslon.management.dto.responseDto.TenantAdminResponse;
import com.maverick.zaslon.management.repository.ManagementUserRepository;
import com.maverick.zaslon.management.repository.OrganizationRepository;
import com.maverick.zaslon.management.repository.RoleRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final ManagementUserRepository managementUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContext tenantContext;
    private final AuditService auditService;

    public OrganizationService(OrganizationRepository organizationRepository,
            ManagementUserRepository managementUserRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            TenantContext tenantContext,
            AuditService auditService) {
        this.organizationRepository = organizationRepository;
        this.managementUserRepository = managementUserRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantContext = tenantContext;
        this.auditService = auditService;
    }

    @Transactional
    public OrganizationResponse create(@Valid OrganizationRequest request) {
        long startedAt = System.nanoTime();
        checkUnique(request.getName(), request.getSlug(), null);

        Organization organization = new Organization();
        organization.setName(request.getName());
        organization.setSlug(request.getSlug());
        organization.setStatus(OrganizationStatus.PENDING);
        organization.setCreatedAt(LocalDateTime.now());
        organization.setUpdatedAt(LocalDateTime.now());

        organizationRepository.save(organization);
        auditService.record(organization, "CREATE_ORGANIZATION", "ORGANIZATION",
                organization.getId().toString(), startedAt, "Organization created in Pending Status");

        return OrganizationResponse.from(organization);
    }

    // The outer condition checks whether the slug exists anywhere in the database.
    private void checkUnique(String name, String slug, Long currentId) {
        if (organizationRepository.existsByNameIgnoreCase(name)) {
            Organization current = currentId == null ? null : organizationRepository.findById(currentId).orElse(null);

            // The inner condition determines whether that existing slug belongs to another
            // organization
            // or to the organization you're currently updating.
            // this means that, "name" is present in different organization and you are
            // creating the same name in onboarding organization .
            if (current == null || !current.getName().equalsIgnoreCase(name)) {
                throw new ApiException(HttpStatus.CONFLICT, "Organization name already exists");
            }
        }

        if (organizationRepository.existsBySlugIgnoreCase(slug)) {
            Organization current = currentId == null ? null : organizationRepository.findById(currentId).orElse(null);

            if (current == null || !current.getSlug().equalsIgnoreCase(slug)) {
                throw new ApiException(HttpStatus.CONFLICT, "Organization slug already exists");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> findAll() {
        List<OrganizationResponse> organizationResponses = new ArrayList<>();
        for (Organization org : organizationRepository.findByStatusNot(OrganizationStatus.DELETED)) {
            organizationResponses.add(OrganizationResponse.from(org));
        }
        return organizationResponses;
    }

    @Transactional(readOnly = true)
    public OrganizationResponse findById(Long id) {
        return OrganizationResponse.from(getOrganization(id));
    }

    private Organization getOrganization(Long id) {
        return organizationRepository.findByIdAndStatusNot(id, OrganizationStatus.DELETED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    @Transactional
    public OrganizationResponse update(Long id, @Valid OrganizationRequest request) {
        long startedAt = System.nanoTime();

        Organization organization = getOrganization(id);

        checkUnique(request.getName(), request.getSlug(), id);

        organization.setName(request.getName());
        organization.setSlug(request.getSlug());
        organization.setUpdatedAt(LocalDateTime.now());

        auditService.record(organization, "UPDATE_ORGANIZATION",
                "ORGANIZATION", id.toString(), startedAt,
                "Organization name and slug updated");

        // no need of save since @transactional is used.
        return OrganizationResponse.from(organization);
    }

    @Transactional
    public OrganizationResponse changeStatus(Long id, @Valid OrganizationStatusRequest request) {
        long startedAt = System.nanoTime();

        Organization organization = getOrganization(id);
        if (request.getStatus().equals(OrganizationStatus.DELETED)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Organization status cannot be deleted. Use Soft delete");
        }

        organization.setStatus(request.getStatus());
        organization.setUpdatedAt(LocalDateTime.now());

        auditService.record(organization, "CHANGE_ORGANIZATION_STATUS",
                "ORGANIZATION", id.toString(),
                startedAt, "Status changed to " + request.getStatus());

        return OrganizationResponse.from(organization);
    }

    @Transactional
    public void delete(Long id) {
        long startedAt = System.nanoTime();

        Organization organization = getOrganization(id);
        organization.setStatus(OrganizationStatus.DELETED);
        organization.setUpdatedAt(LocalDateTime.now());
        organization.setDeletedAt(LocalDateTime.now());

        auditService.record(organization, "DELETE_ORGANIZATION",
                "ORGANIZATION", id.toString(), startedAt,
                "Organization soft-deleted");
    }

    // Action performed by Platform admin for tenant Admin
    @Transactional
    public TenantAdminResponse createTenantAdmin(@Valid Long organizationId, TenantAdminRequest request) {
        Long startedAt = System.nanoTime();

        Organization organization = getOrganization(organizationId);

        if (managementUserRepository.findByOrganizationId(organizationId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This Organization already has a tenant admin");
        }

        if (managementUserRepository.existsByUsername(request.getUsername())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
        }

        Role role = roleRepository.findByName("TENANT_ADMIN")
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_ADMIN role not found"));

        ManagementUser managementUser = new ManagementUser();
        managementUser.setOrganization(organization);
        managementUser.setUsername(request.getUsername());
        managementUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        managementUser.setRole(role);
        managementUser.setEnabled(request.isEnabled());
        managementUser.setCreatedAt(LocalDateTime.now());
        managementUser.setUpdatedAt(LocalDateTime.now());

        managementUserRepository.save(managementUser);

        auditService.record(organization, "CREATE_TENANT_ADMIN",
                "MANAGEMENT_USER", managementUser.getId().toString(),
                startedAt, "Tenant administrator created");

        return TenantAdminResponse.from(managementUser);
    }

    public TenantAdminResponse getTenantAdmin(@Valid Long organizationId) {
        return TenantAdminResponse.from(getTenantAdminEntity(organizationId));
    }

    private ManagementUser getTenantAdminEntity(Long organizationId) {
        getOrganization(organizationId);

        return managementUserRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Tenant admin not found for organization ID: " + organizationId));
    }

    @Transactional
    public TenantAdminResponse updateTenantAdmin(Long organizationId, @Valid TenantAdminRequest request) {
        Long startedAt = System.nanoTime();

        ManagementUser user = getTenantAdminEntity(organizationId);

        if (!user.getUsername().equals(request.getUsername()) &&
                managementUserRepository.existsByUsername(request.getUsername())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
        }

        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(request.isEnabled());
        user.setUpdatedAt(LocalDateTime.now());

        auditService.record(user.getOrganization(), "UPDATE_TENANT_ADMIN",
                "MANAGEMENT_USER", user.getId().toString(), startedAt,
                "Tenant administrator updated");

        return TenantAdminResponse.from(user);
    }

    @Transactional
    public void deleteTenantAdmin(Long organizationId) {
        long startedAt = System.nanoTime();

        ManagementUser user = getTenantAdminEntity(organizationId);
        auditService.record(user.getOrganization(), "DELETE_TENANT_ADMIN",
                "MANAGEMENT_USER", user.getId().toString(), startedAt,
                "Tenant administrator deleted");

        managementUserRepository.delete(user);
    }

    // Methods for tenant Admin
    @Transactional(readOnly = true)
    public OrganizationResponse getOwnOrganization() {
        Organization organization = tenantContext.getOrganization();
        return OrganizationResponse.from(organization);
    }

    @Transactional
    public OrganizationResponse updateOwnOrganization(@Valid TenantOrganizationRequest request) {
        long startedAt = System.nanoTime();
        Organization organization = tenantContext.getOrganization();

        if (!organization.getName().equalsIgnoreCase(request.getName()) &&
                organizationRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ApiException(HttpStatus.CONFLICT, "Organization name already exists");
        }
        organization.setName(request.getName());
        organization.setUpdatedAt(LocalDateTime.now());

        auditService.record(organization, "UPDATE_OWN_ORGANIZATION",
                "ORGANIZATION",
                organization.getId().toString(),
                startedAt,
                "Tenant updated its organization name");

        return OrganizationResponse.from(organization);
    }
}
