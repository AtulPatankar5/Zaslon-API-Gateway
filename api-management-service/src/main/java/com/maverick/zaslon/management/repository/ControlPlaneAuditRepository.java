package com.maverick.zaslon.management.repository;

import com.maverick.zaslon.management.Entity.ControlPlaneAuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlPlaneAuditRepository extends JpaRepository<ControlPlaneAuditRecord, Long> {
}
