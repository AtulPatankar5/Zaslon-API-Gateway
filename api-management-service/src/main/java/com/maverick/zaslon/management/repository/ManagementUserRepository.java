package com.maverick.zaslon.management.repository;

import com.maverick.zaslon.management.Entity.ManagementUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ManagementUserRepository extends JpaRepository<ManagementUser, Long> {

    Optional<ManagementUser> findByUsername(String username);

    Optional<ManagementUser> findByOrganizationId(Long organizationId);

    boolean existsByUsername( String username);
}
