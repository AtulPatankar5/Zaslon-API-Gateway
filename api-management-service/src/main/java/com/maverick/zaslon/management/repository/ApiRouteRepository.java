package com.maverick.zaslon.management.repository;

import com.maverick.zaslon.management.entity.ApiRoute;
import com.maverick.zaslon.management.entity.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiRouteRepository extends JpaRepository<ApiRoute, Long> {
    boolean existsByOrganizationIdAndRouteId(Long id, String routeId);

    List<ApiRoute> findAllByOrganizationId(Long id);

    Optional<ApiRoute> findByIdAndOrganizationId(Long id, Long organizationId);

    List<ApiRoute> findAllByActiveTrueAndOrganizationStatus(OrganizationStatus organizationStatus);
}
