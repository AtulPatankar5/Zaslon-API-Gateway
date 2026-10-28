package com.maverick.zaslon.management.dto.responseDto;

import com.maverick.zaslon.management.Entity.Organization;
import com.maverick.zaslon.management.Entity.OrganizationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OrganizationResponse {

    private Long id;

    private String name;
    private String slug;

    private OrganizationStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    //convert a organization entity to organization Response DTO
    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getStatus(),
                organization.getCreatedAt(),
                organization.getUpdatedAt()
        );
    }

}
